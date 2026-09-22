package com.sharp.controller;

import com.sharp.common.CurrentUser;
import com.sharp.common.Result;
import com.sharp.dto.AppleHideEmailRequest;
import com.sharp.entity.AppleAccount;
import com.sharp.entity.AppleHideEmail;
import com.sharp.service.AppleService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/** Apple ID 与隐藏邮箱录入。鉴权由 WebConfig 的拦截器统一覆盖 /api/**。 */
@RestController
@RequestMapping("/api/apple")
public class AppleController {

    private final AppleService service;

    public AppleController(AppleService service) {
        this.service = service;
    }

    /* ---------- Apple ID ---------- */

    /** 分页查 Apple ID，前端下拉搜索与列表筛选共用。 */
    @GetMapping("/accounts")
    public Result<Map<String, Object>> listAccounts(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(toPageData(service.listAccounts(keyword, page, size), page, size));
    }

    /** 新增 Apple ID（已存在则复用）。 */
    @PostMapping("/accounts")
    public Result<AppleAccount> saveAccount(@RequestBody AppleHideEmailRequest req) {
        return Result.ok(service.saveAccount(req.getAppleId(), req.getNote(), currentUser()));
    }

    @DeleteMapping("/accounts/{id}")
    public Result<Void> deleteAccount(@PathVariable Long id) {
        service.deleteAccount(id);
        return Result.ok();
    }

    /* ---------- 隐藏邮箱 ---------- */

    @GetMapping("/hide-emails")
    public Result<Map<String, Object>> listHideEmails(
            @RequestParam(required = false) Long appleAccountId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(toPageData(service.listHideEmails(appleAccountId, keyword, page, size), page, size));
    }

    /** 录入一条隐藏邮箱：Apple ID（选已有或新建）+ 隐藏邮箱 + HackerOne 邮箱 + 谷歌别名邮箱。 */
    @PostMapping("/hide-emails")
    public Result<AppleHideEmail> saveHideEmail(@RequestBody AppleHideEmailRequest req) {
        return Result.ok(service.saveHideEmail(
                req.getAppleAccountId(), req.getAppleId(), req.getHideEmail(),
                req.getRedirectEmail(), req.getGoogleAliasEmail(), currentUser()));
    }

    /** 局部更新：只改传进来的字段。 */
    @PutMapping("/hide-emails/{id}")
    public Result<AppleHideEmail> updateHideEmail(@PathVariable Long id, @RequestBody AppleHideEmail patch) {
        return Result.ok(service.updateHideEmail(id, patch));
    }

    @DeleteMapping("/hide-emails/{id}")
    public Result<Void> deleteHideEmail(@PathVariable Long id) {
        service.deleteHideEmail(id);
        return Result.ok();
    }

    /* ---------- 内部 ---------- */

    /** 录入人取自登录用户（JWT），与 EmailAccountController 保持一致。 */
    private String currentUser() {
        String user = CurrentUser.get();
        return (user == null || user.isBlank()) ? "unknown" : user;
    }

    private Map<String, Object> toPageData(Page<?> result, int page, int size) {
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getContent());
        data.put("total", result.getTotalElements());
        data.put("page", page);
        data.put("size", size);
        return data;
    }
}
