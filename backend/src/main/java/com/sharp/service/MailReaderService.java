package com.sharp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharp.dto.MailMessageDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Outlook 邮件取件（复刻 2fa.run/mail）：
 * refreshToken + clientId → 微软 OAuth2 换 access_token → Microsoft Graph 读取 Outlook 最新邮件，
 * 并从主题/正文中提取验证码。只读，不改动/删除邮件。
 *
 * 为何用 Graph 而非 IMAP：自 2024-12 起微软对个人账号(outlook.com/hotmail/live)的 IMAP OAuth2
 * 存在服务端回归——token 认证成功但 IMAP 会话被拒（NO User is authenticated but not connected）。
 * Graph REST 接口不受影响，个人账号可正常拉信。（Gmail 见 GmailReaderService，其 IMAP 正常可用。）
 */
@Service
public class MailReaderService {

    @Value("${mail-reader.token-url}")
    private String tokenUrl;
    @Value("${mail-reader.scope}")
    private String scope;
    @Value("${mail-reader.scope-fallback}")
    private String scopeFallback;
    @Value("${mail-reader.graph-base}")
    private String graphBase;
    @Value("${mail-reader.connect-timeout-ms}")
    private int connectTimeoutMs;
    @Value("${mail-reader.read-timeout-ms}")
    private int readTimeoutMs;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * 用 refresh_token 换 access_token（受众须为 Graph，见 mail-reader.scope）。
     * 首选 scope 若因授权范围问题被拒（AADSTS70000/65001：该凭据当初未同意所请求的权限），
     * 用 scope-fallback（默认 .default = 该 client 在 Graph 上已同意的全部权限）再试一次。
     * 全部失败时把微软返回的错误原文抛出。
     */
    public String getAccessToken(String clientId, String refreshToken) {
        TokenResponse first = requestToken(clientId, refreshToken, scope);
        if (first.accessToken() != null) {
            return first.accessToken();
        }
        boolean scopeDenied = first.error() != null
                && (first.error().contains("AADSTS70000") || first.error().contains("AADSTS65001"));
        if (scopeDenied && !MailTextUtil.isBlank(scopeFallback)) {
            TokenResponse retry = requestToken(clientId, refreshToken, scopeFallback);
            if (retry.accessToken() != null) {
                return retry.accessToken();
            }
            throw new IllegalArgumentException("获取 access_token 失败：" + MailTextUtil.trim(first.error(), 260)
                    + "；改用 .default 重试仍失败：" + MailTextUtil.trim(retry.error(), 260));
        }
        throw new IllegalArgumentException("获取 access_token 失败：" + MailTextUtil.trim(first.error(), 500));
    }

    /** 单次 token 请求；scope 为空则不带该参数。成功返回 access_token，失败返回错误原文。 */
    private TokenResponse requestToken(String clientId, String refreshToken, String scopeValue) {
        String form = "client_id=" + enc(clientId)
                + "&grant_type=refresh_token"
                + "&refresh_token=" + enc(refreshToken)
                + (MailTextUtil.isBlank(scopeValue) ? "" : "&scope=" + enc(scopeValue));
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
                return new TokenResponse(json.get("access_token").asText(), null);
            }
            String err = json.hasNonNull("error_description")
                    ? json.get("error_description").asText()
                    : (json.hasNonNull("error") ? json.get("error").asText() : resp.body());
            return new TokenResponse(null, err);
        } catch (Exception e) {
            throw new IllegalArgumentException("请求微软登录服务失败：" + e.getMessage());
        }
    }

    /** token 端点响应：二者必有其一。 */
    private record TokenResponse(String accessToken, String error) {
    }

    /** 经 Microsoft Graph 读取最新邮件。folder：inbox / junk / all。 */
    public List<MailMessageDto> fetch(String email, String accessToken, String folder, int limit) {
        String f = folder == null ? "all" : folder.trim().toLowerCase();
        List<MailMessageDto> result = new ArrayList<>();
        try {
            if ("inbox".equals(f) || "all".equals(f)) {
                fetchFolder(accessToken, "inbox", "收件箱", limit, result);
            }
            if ("junk".equals(f) || "all".equals(f)) {
                fetchFolder(accessToken, "junkemail", "垃圾箱", limit, result);
            }
            // 多文件夹合并后按时间倒序，再截断到 limit
            result.sort((a, b) -> nullSafe(b.getDate()).compareTo(nullSafe(a.getDate())));
            if (result.size() > limit) {
                return new ArrayList<>(result.subList(0, limit));
            }
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Graph 取件失败：" + e.getMessage()
                    + "｜诊断：" + describeToken(accessToken, email));
        }
    }

    /**
     * 拉取单个邮件文件夹的最新 limit 封（按接收时间倒序）。
     * folderId 用 Graph 众所周知名：inbox / junkemail。
     */
    private void fetchFolder(String accessToken, String folderId, String label, int limit,
                             List<MailMessageDto> out) throws Exception {
        String url = graphBase + "/me/mailFolders/" + folderId + "/messages"
                + "?$top=" + Math.max(1, limit)
                + "&$select=subject,from,receivedDateTime,body,bodyPreview"
                + "&$orderby=receivedDateTime%20desc";
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(readTimeoutMs))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode json = objectMapper.readTree(resp.body());
        if (resp.statusCode() != 200) {
            String msg = json.path("error").path("message").asText("");
            String code = json.path("error").path("code").asText("");
            throw new IllegalArgumentException("Graph 返回 " + resp.statusCode()
                    + (MailTextUtil.isBlank(code) ? "" : "（" + code + "）")
                    + "：" + (MailTextUtil.isBlank(msg) ? MailTextUtil.trim(resp.body(), 300) : MailTextUtil.trim(msg, 300)));
        }
        JsonNode value = json.path("value");
        if (value.isArray()) {
            for (JsonNode m : value) {
                out.add(toDto(m, label));
            }
        }
    }

    /** Graph 消息 JSON → DTO。Graph 的主题/发件人已解码，正文按 contentType 决定是否剥 HTML。 */
    private MailMessageDto toDto(JsonNode m, String folderLabel) {
        MailMessageDto dto = new MailMessageDto();
        dto.setFolder(folderLabel);
        dto.setSubject(textOrNull(m.path("subject")));

        JsonNode fromAddr = m.path("from").path("emailAddress");
        if (!fromAddr.isMissingNode()) {
            dto.setFrom(textOrNull(fromAddr.path("address")));
            dto.setFromName(textOrNull(fromAddr.path("name")));
        }

        String received = textOrNull(m.path("receivedDateTime"));
        if (received != null) {
            try {
                dto.setDate(Instant.parse(received).atZone(ZoneId.systemDefault()).format(ISO));
            } catch (Exception ignore) {
                dto.setDate(received);
            }
        }

        JsonNode bodyNode = m.path("body");
        String contentType = bodyNode.path("contentType").asText("text");
        String content = bodyNode.path("content").asText("");
        String body = "html".equalsIgnoreCase(contentType) ? MailTextUtil.htmlToText(content) : content;
        if (MailTextUtil.isBlank(body)) {
            body = m.path("bodyPreview").asText("");
        }
        dto.setBody(body);
        dto.setPreview(MailTextUtil.trim(body.replaceAll("\\s+", " ").trim(), 200));
        dto.setVerifyCode(MailTextUtil.extractCode(dto.getSubject(), body));
        return dto;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String textOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    /**
     * 解出 access_token 的关键声明用于排障：受众(aud)、授权范围(scp)、token 所属账号，
     * 并与调用方传入的邮箱比对。个人账号的 token 常是不透明串（非 JWT），解不出属正常现象。
     * 只输出声明，不输出 token 本身。
     */
    private String describeToken(String accessToken, String email) {
        try {
            String[] parts = accessToken == null ? new String[0] : accessToken.split("\\.");
            if (parts.length < 2) {
                return "token 非 JWT 格式（个人账号常见），无法解出受众；传入邮箱=" + nullSafe(email);
            }
            byte[] payload = java.util.Base64.getUrlDecoder().decode(parts[1]);
            JsonNode claims = objectMapper.readTree(payload);
            String aud = claims.path("aud").asText("");
            String scp = claims.path("scp").asText("");
            String owner = firstNonBlank(
                    claims.path("upn").asText(""),
                    claims.path("email").asText(""),
                    claims.path("preferred_username").asText(""),
                    claims.path("unique_name").asText(""));
            String mismatch = (!MailTextUtil.isBlank(owner) && !MailTextUtil.isBlank(email)
                    && !owner.equalsIgnoreCase(email.trim())) ? "（与传入邮箱不一致！）" : "";
            return "aud=" + aud + "，scp=" + scp
                    + "，token 账号=" + (MailTextUtil.isBlank(owner) ? "未知" : owner) + mismatch
                    + "，传入邮箱=" + nullSafe(email);
        } catch (Exception ex) {
            return "token 声明解析失败（" + ex.getClass().getSimpleName() + "）；传入邮箱=" + nullSafe(email);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!MailTextUtil.isBlank(v)) {
                return v;
            }
        }
        return "";
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
