package com.sharp.controller;

import com.sharp.common.Result;
import com.sharp.dto.MailFetchRequest;
import com.sharp.dto.MailMessageDto;
import com.sharp.entity.EmailAccount;
import com.sharp.repository.EmailAccountRepository;
import com.sharp.service.GmailReaderService;
import com.sharp.service.Mail012eService;
import com.sharp.service.MailReaderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 邮件取件接口（复刻 2fa.run/mail）。支持 Outlook(Graph) 与 Gmail(IMAP：OAuth 或应用专用密码)。
 */
@RestController
@RequestMapping("/api/mail")
public class MailController {

    /** clientId 合法性校验：标准 GUID。 */
    private static final Pattern GUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final MailReaderService mailReaderService;
    private final GmailReaderService gmailReaderService;
    private final Mail012eService mail012eService;
    private final EmailAccountRepository accountRepository;

    @Value("${mail-reader.default-client-id:}")
    private String defaultClientId;

    public MailController(MailReaderService mailReaderService,
                          GmailReaderService gmailReaderService,
                          Mail012eService mail012eService,
                          EmailAccountRepository accountRepository) {
        this.mailReaderService = mailReaderService;
        this.gmailReaderService = gmailReaderService;
        this.mail012eService = mail012eService;
        this.accountRepository = accountRepository;
    }

    /** 用凭据实时读取最新邮件并提取验证码。 */
    @PostMapping("/fetch")
    public Result<List<MailMessageDto>> fetch(@RequestBody MailFetchRequest req) {
        // 取凭据：传了 accountId 则从库里取，并以账号的 emailType 覆盖 provider
        Creds c = req.getAccountId() != null ? fromAccount(req) : fromRequest(req);

        int limit = req.getLimit() == null ? 10 : Math.min(Math.max(req.getLimit(), 1), 30);
        String folder = isBlank(req.getFolder()) ? "all" : req.getFolder();

        List<MailMessageDto> messages = switch (c.provider) {
            case "gmail" -> fetchGmail(c, folder, limit);
            case "012e" -> mail012eService.fetch(c.extraUrl);
            default -> fetchOutlook(c, folder, limit);
        };
        return Result.ok(messages);
    }

    /** Outlook：refreshToken + clientId → Graph。clientId 非法则回退到默认公共 client。 */
    private List<MailMessageDto> fetchOutlook(Creds c, String folder, int limit) {
        String clientId = normalizeClientId(c.clientId);
        if (isBlank(c.email) || isBlank(c.refreshToken) || isBlank(clientId)) {
            throw new IllegalArgumentException("Outlook 取件需 email、refreshToken、clientId（或传有效的 accountId）");
        }
        String accessToken = mailReaderService.getAccessToken(clientId, c.refreshToken);
        return mailReaderService.fetch(c.email, accessToken, folder, limit);
    }

    /** clientId 是合法 GUID 就用它，否则（缺失/误存说明文字）回退到配置的默认公共 clientId。 */
    private String normalizeClientId(String clientId) {
        if (clientId != null && GUID.matcher(clientId.trim()).matches()) {
            return clientId.trim();
        }
        return defaultClientId == null ? "" : defaultClientId.trim();
    }

    /** Gmail：oauth（refreshToken+clientId+clientSecret）或 password（应用专用密码）。 */
    private List<MailMessageDto> fetchGmail(Creds c, String folder, int limit) {
        boolean password = "password".equalsIgnoreCase(c.authMode);
        if (password) {
            if (isBlank(c.email) || isBlank(c.password)) {
                throw new IllegalArgumentException("Gmail 应用专用密码方式需 email 与 password（16 位 app password）");
            }
            return gmailReaderService.fetchByAppPassword(c.email, c.password, folder, limit);
        }
        if (isBlank(c.email) || isBlank(c.refreshToken) || isBlank(c.clientId) || isBlank(c.clientSecret)) {
            throw new IllegalArgumentException("Gmail OAuth 方式需 email、refreshToken、clientId、clientSecret");
        }
        String accessToken = gmailReaderService.getAccessToken(c.clientId, c.clientSecret, c.refreshToken);
        return gmailReaderService.fetchByOAuth(c.email, accessToken, folder, limit);
    }

    /** 从已入库账号取凭据，provider 由 emailType 决定。 */
    private Creds fromAccount(MailFetchRequest req) {
        EmailAccount acc = accountRepository.findById(req.getAccountId())
                .orElseThrow(() -> new IllegalArgumentException("账号不存在：" + req.getAccountId()));
        Creds c = new Creds();
        c.provider = normalizeProvider(acc.getEmailType());
        c.email = acc.getEmail();
        c.refreshToken = acc.getRefreshToken();
        c.clientId = acc.getClientId();
        c.clientSecret = req.getClientSecret();   // 库中暂无独立列，允许请求补充
        c.password = acc.getPassword();
        c.extraUrl = acc.getExtraUrl();           // 012e 取件链接
        // 认证方式：请求显式指定优先，否则据库中字段推断（有 refreshToken 走 oauth，否则应用专用密码）
        c.authMode = !isBlank(req.getAuthMode()) ? req.getAuthMode()
                : (isBlank(acc.getRefreshToken()) ? "password" : "oauth");
        return c;
    }

    /** 从请求体直接取凭据。 */
    private Creds fromRequest(MailFetchRequest req) {
        Creds c = new Creds();
        c.provider = normalizeProvider(req.getProvider());
        c.authMode = isBlank(req.getAuthMode()) ? "oauth" : req.getAuthMode();
        c.email = req.getEmail();
        c.refreshToken = req.getRefreshToken();
        c.clientId = req.getClientId();
        c.clientSecret = req.getClientSecret();
        c.password = req.getPassword();
        c.extraUrl = req.getExtraUrl();
        return c;
    }

    /** emailType/provider 归一：gmail→gmail，012e→012e，其余（含 outlook / 空）→ outlook。 */
    private static String normalizeProvider(String raw) {
        if (raw == null) {
            return "outlook";
        }
        String t = raw.trim().toLowerCase();
        if ("gmail".equals(t)) {
            return "gmail";
        }
        if ("012e".equals(t)) {
            return "012e";
        }
        return "outlook";
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 归一化后的凭据。 */
    private static class Creds {
        String provider;
        String authMode;
        String email;
        String refreshToken;
        String clientId;
        String clientSecret;
        String password;
        String extraUrl;
    }
}
