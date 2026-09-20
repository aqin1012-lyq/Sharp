package com.sharp.controller;

import com.sharp.common.Result;
import com.sharp.dto.ParseRequest;
import com.sharp.entity.EmailAccount;
import com.sharp.service.EmailAccountService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/email")
public class EmailAccountController {

    private final EmailAccountService service;

    public EmailAccountController(EmailAccountService service) {
        this.service = service;
    }

    /** 解析预览（不落库） */
    @PostMapping("/parse")
    public Result<List<EmailAccount>> parse(@Valid @RequestBody ParseRequest req) {
        return Result.ok(service.parse(req.getEmailType(), req.getRawData(), req.getFields()));
    }

    /** 解析并保存。录入人取自登录用户（JWT）。 */
    @PostMapping("/save")
    public Result<List<EmailAccount>> save(@Valid @RequestBody ParseRequest req) {
        String createdBy = com.sharp.common.CurrentUser.get();
        if (createdBy == null || createdBy.isBlank()) {
            createdBy = "unknown";
        }
        return Result.ok(service.parseAndSave(req.getEmailType(), req.getRawData(), req.getFields(), createdBy));
    }

    /** 按录入人统计邮箱录入量。 */
    @GetMapping("/stats/by-user")
    public Result<List<Map<String, Object>>> statsByUser() {
        return Result.ok(service.statsByUser());
    }

    /** 分页查询 */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String emailType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<EmailAccount> result = service.list(emailType, keyword, page, size);
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getContent());
        data.put("total", result.getTotalElements());
        data.put("page", page);
        data.put("size", size);
        return Result.ok(data);
    }

    /** 更新单条 */
    @PutMapping("/{id}")
    public Result<EmailAccount> update(@PathVariable Long id, @RequestBody EmailAccount account) {
        account.setId(id);
        return Result.ok(service.update(account));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }
}
