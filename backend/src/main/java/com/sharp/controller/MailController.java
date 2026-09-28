package com.sharp.controller;

import com.sharp.common.Result;
import com.sharp.dto.MailFetchRequest;
import com.sharp.dto.MailMessageDto;
import com.sharp.service.MailFetchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 邮件取件接口（复刻 2fa.run/mail）。支持 Outlook(Graph) 与 Gmail(IMAP：OAuth 或应用专用密码)。
 * 具体编排在 {@link MailFetchService}，Apple 的倒推接码复用同一套逻辑。
 */
@RestController
@RequestMapping("/api/mail")
public class MailController {

    private final MailFetchService mailFetchService;

    public MailController(MailFetchService mailFetchService) {
        this.mailFetchService = mailFetchService;
    }

    /** 用凭据实时读取最新邮件并提取验证码。 */
    @PostMapping("/fetch")
    public Result<List<MailMessageDto>> fetch(@RequestBody MailFetchRequest req) {
        return Result.ok(mailFetchService.fetchByRequest(req));
    }
}
