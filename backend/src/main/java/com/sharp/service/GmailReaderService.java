package com.sharp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharp.dto.MailMessageDto;
import jakarta.mail.Folder;
import jakarta.mail.FetchProfile;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Gmail 邮件取件。两种认证方式，最终都走 imap.gmail.com 读信（Gmail 的 IMAP OAuth 正常可用，
 * 无 Outlook 那种服务端回归）：
 * 1) OAuth：refreshToken + clientId + clientSecret → Google OAuth2 换 access_token → IMAP(XOAUTH2)
 * 2) 应用专用密码：邮箱 + 16 位 app password → IMAP LOGIN
 * 只读，不改动/删除邮件。
 */
@Service
public class GmailReaderService {

    @Value("${gmail-reader.token-url}")
    private String tokenUrl;
    @Value("${gmail-reader.scope}")
    private String scope;
    @Value("${gmail-reader.imap-host}")
    private String imapHost;
    @Value("${gmail-reader.imap-port}")
    private int imapPort;
    @Value("${gmail-reader.spam-folder}")
    private String spamFolder;
    @Value("${gmail-reader.connect-timeout-ms}")
    private int connectTimeoutMs;
    @Value("${gmail-reader.read-timeout-ms}")
    private int readTimeoutMs;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /** 用 refresh_token 经 Google OAuth2 换 access_token。失败时把 Google 返回的错误原文抛出。 */
    public String getAccessToken(String clientId, String clientSecret, String refreshToken) {
        String form = "client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&grant_type=refresh_token"
                + "&refresh_token=" + enc(refreshToken)
                + (MailTextUtil.isBlank(scope) ? "" : "&scope=" + enc(scope));
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(tokenUrl))
                    .timeout(Duration.ofMillis(readTimeoutMs))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(resp.body());
            if (resp.statusCode() == 200 && json.hasNonNull("access_token")) {
                return json.get("access_token").asText();
            }
            String err = json.hasNonNull("error_description")
                    ? json.get("error_description").asText()
                    : (json.hasNonNull("error") ? json.get("error").asText() : resp.body());
            throw new IllegalArgumentException("获取 Google access_token 失败：" + MailTextUtil.trim(err, 500));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("请求 Google 登录服务失败：" + e.getMessage());
        }
    }

    /**
     * IMAP(XOAUTH2) 读信：secret 传 access_token。
     * @param email 邮箱地址
     * @param accessToken Google OAuth access_token
     */
    public List<MailMessageDto> fetchByOAuth(String email, String accessToken, String folder, int limit) {
        return fetch(email, accessToken, true, folder, limit);
    }

    /**
     * IMAP 应用专用密码读信：secret 传 16 位 app password。
     * @param email 邮箱地址
     * @param appPassword 应用专用密码（非登录密码）
     */
    public List<MailMessageDto> fetchByAppPassword(String email, String appPassword, String folder, int limit) {
        return fetch(email, appPassword, false, folder, limit);
    }

    /** 连接 imap.gmail.com 读取最新邮件。xoauth2=true 用 XOAUTH2 机制，否则用普通 LOGIN。 */
    private List<MailMessageDto> fetch(String email, String secret, boolean xoauth2, String folder, int limit) {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", imapHost);
        props.put("mail.imaps.port", String.valueOf(imapPort));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", String.valueOf(connectTimeoutMs));
        props.put("mail.imaps.timeout", String.valueOf(readTimeoutMs));
        props.put("mail.imaps.writetimeout", String.valueOf(readTimeoutMs));
        if (xoauth2) {
            props.put("mail.imaps.auth.mechanisms", "XOAUTH2");
            props.put("mail.imaps.auth.login.disable", "true");
            props.put("mail.imaps.auth.plain.disable", "true");
        }

        Session session = Session.getInstance(props);
        List<MailMessageDto> result = new ArrayList<>();
        Store store = null;
        try {
            store = session.getStore("imaps");
            // XOAUTH2 下 password 位置传 access_token；应用专用密码则直接传密码
            store.connect(imapHost, email, secret);

            String f = folder == null ? "all" : folder.trim().toLowerCase();
            boolean wantInbox = "inbox".equals(f) || "all".equals(f);
            boolean wantJunk = "junk".equals(f) || "all".equals(f);

            if (wantInbox) {
                readFolder(store, "INBOX", "收件箱", limit, result);
            }
            if (wantJunk) {
                // Gmail 垃圾箱是特殊文件夹 [Gmail]/Spam（本地化环境名可能不同，用配置项）
                readFolder(store, spamFolder, "垃圾箱", limit, result);
            }
            result.sort((a, b) -> nullSafe(b.getDate()).compareTo(nullSafe(a.getDate())));
            if (result.size() > limit) {
                return new ArrayList<>(result.subList(0, limit));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Gmail IMAP 取件失败：" + e.getMessage()
                    + "｜方式=" + (xoauth2 ? "OAuth(XOAUTH2)" : "应用专用密码")
                    + "，邮箱=" + nullSafe(email) + "，IMAP=" + imapHost + ":" + imapPort);
        } finally {
            if (store != null) {
                try {
                    store.close();
                } catch (Exception ignore) {
                    // 关闭失败不影响已取回的结果
                }
            }
        }
    }

    /** 读取单个文件夹的最新 limit 封；文件夹不存在返回 false。 */
    private boolean readFolder(Store store, String name, String label, int limit, List<MailMessageDto> out) {
        Folder box = null;
        try {
            box = store.getFolder(name);
            if (box == null || !box.exists()) {
                return false;
            }
            box.open(Folder.READ_ONLY);
            int total = box.getMessageCount();
            if (total <= 0) {
                return true;
            }
            int start = Math.max(1, total - limit + 1);
            Message[] msgs = box.getMessages(start, total);
            // 一次性预取信封/标志/正文结构，减少逐封往返（高延迟网络下是超时主因）
            FetchProfile fp = new FetchProfile();
            fp.add(FetchProfile.Item.ENVELOPE);
            fp.add(FetchProfile.Item.CONTENT_INFO);
            box.fetch(msgs, fp);
            // getMessages 按邮件号升序（旧→新），倒序成新→旧
            for (int i = msgs.length - 1; i >= 0; i--) {
                out.add(toDto(msgs[i], label));
            }
            return true;
        } catch (Exception e) {
            // 单个文件夹读取失败不影响其他文件夹
            return box != null;
        } finally {
            if (box != null && box.isOpen()) {
                try {
                    box.close(false);
                } catch (Exception ignore) {
                    // 忽略关闭异常
                }
            }
        }
    }

    private MailMessageDto toDto(Message msg, String folderLabel) throws Exception {
        MailMessageDto dto = new MailMessageDto();
        dto.setFolder(folderLabel);
        dto.setSubject(MailTextUtil.decodeMime(msg.getSubject()));

        jakarta.mail.Address[] froms = msg.getFrom();
        if (froms != null && froms.length > 0 && froms[0] instanceof InternetAddress ia) {
            dto.setFrom(ia.getAddress());
            dto.setFromName(MailTextUtil.decodeMime(ia.getPersonal()));
        } else if (froms != null && froms.length > 0) {
            dto.setFrom(froms[0].toString());
        }

        Date received = msg.getReceivedDate() != null ? msg.getReceivedDate() : msg.getSentDate();
        if (received != null) {
            dto.setDate(received.toInstant().atZone(ZoneId.systemDefault()).format(ISO));
        }

        String body = MailTextUtil.extractText(msg);
        dto.setBody(body);
        dto.setBodyHtml(MailTextUtil.sanitizeHtml(MailTextUtil.extractHtml(msg)));
        dto.setPreview(MailTextUtil.trim(body.replaceAll("\\s+", " ").trim(), 200));
        dto.setVerifyCode(MailTextUtil.extractCode(dto.getSubject(), body));
        return dto;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
