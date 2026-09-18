package com.sharp.controller;

import com.sharp.common.Result;
import com.sharp.dto.MailFetchRequest;
import com.sharp.dto.MailMessageDto;
import com.sharp.entity.EmailAccount;
import com.sharp.repository.EmailAccountRepository;
import com.sharp.service.MailReaderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 邮件取件接口（复刻 2fa.run/mail）。
 */
@RestController
@RequestMapping("/api/mail")
public class MailController {

    private final MailReaderService mailReaderService;
    private final EmailAccountRepository accountRepository;

    public MailController(MailReaderService mailReaderService, EmailAccountRepository accountRepository) {
        this.mailReaderService = mailReaderService;
        this.accountRepository = accountRepository;
    }

    /** 用 Outlook 凭据实时读取最新邮件并提取验证码。 */
    @PostMapping("/fetch")
    public Result<List<MailMessageDto>> fetch(@RequestBody MailFetchRequest req) {
        String email = req.getEmail();
        String refreshToken = req.getRefreshToken();
        String clientId = req.getClientId();

        // 传了 accountId 则从库里取凭据
        if (req.getAccountId() != null) {
            EmailAccount acc = accountRepository.findById(req.getAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("账号不存在：" + req.getAccountId()));
            email = acc.getEmail();
            refreshToken = acc.getRefreshToken();
            clientId = acc.getClientId();
        }

        if (isBlank(email) || isBlank(refreshToken) || isBlank(clientId)) {
            throw new IllegalArgumentException("email、refreshToken、clientId 均不能为空（或传有效的 accountId）");
        }

        int limit = req.getLimit() == null ? 10 : Math.min(Math.max(req.getLimit(), 1), 30);
        String folder = isBlank(req.getFolder()) ? "all" : req.getFolder();

        String accessToken = mailReaderService.getAccessToken(clientId, refreshToken);
        List<MailMessageDto> messages = mailReaderService.fetch(email, accessToken, folder, limit);
        return Result.ok(messages);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
