package com.sharp.service;

import com.sharp.dto.MailMessageDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 012e 取件：账号自带一个「取件链接」（getcode API），GET 即返回最新邮件的 HTML。
 * 无需密码/OAuth——把 HTML 剥成纯文本并提取验证码即可。
 */
@Service
public class Mail012eService {

    @Value("${mail-012e.connect-timeout-ms:12000}")
    private int connectTimeoutMs;
    @Value("${mail-012e.read-timeout-ms:20000}")
    private int readTimeoutMs;

    /** 请求取件链接，返回单封邮件（纯文本 + 验证码）。 */
    public List<MailMessageDto> fetch(String fetchUrl) {
        if (fetchUrl == null || fetchUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("012e 取件需要取件链接");
        }
        String url = fetchUrl.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("取件链接格式不正确（需以 http:// 或 https:// 开头）");
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(readTimeoutMs))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new IllegalArgumentException("012e 取件链接返回 " + resp.statusCode());
            }
            String raw = resp.body() == null ? "" : resp.body();
            String body = MailTextUtil.htmlToText(raw);
            if (MailTextUtil.isBlank(body)) {
                body = raw;  // 兜底：非 HTML 时直接用原文
            }

            List<MailMessageDto> result = new ArrayList<>();
            MailMessageDto dto = new MailMessageDto();
            dto.setFolder("取件");
            dto.setFrom("012e");
            dto.setBody(body);
            dto.setPreview(MailTextUtil.trim(body.replaceAll("\\s+", " ").trim(), 200));
            dto.setVerifyCode(MailTextUtil.extractCode(null, body));
            result.add(dto);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("012e 取件失败：" + e.getMessage());
        }
    }
}
