package com.sharp.controller;

import com.sharp.common.CurrentUser;
import com.sharp.common.Result;
import com.sharp.dto.AppleRequest;
import com.sharp.entity.AppleAccount;
import com.sharp.entity.AppleForwardEmail;
import com.sharp.entity.AppleHackerEmail;
import com.sharp.entity.AppleHideEmail;
import com.sharp.service.AppleCodeService;
import com.sharp.service.AppleService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Apple 录入的三个子模块。鉴权由 WebConfig 的拦截器统一覆盖 /api/**。
 * 一、/forward-emails  Apple ID 的转发邮箱（可多个，其一为当前）
 * 二、/hide-emails     Apple ID 的隐藏邮箱
 * 三、/hacker-emails   隐藏邮箱与黑客邮箱（一对一）
 */
@RestController
@RequestMapping("/api/apple")
public class AppleController {

    private final AppleService service;
    private final AppleCodeService codeService;

    public AppleController(AppleService service, AppleCodeService codeService) {
        this.service = service;
        this.codeService = codeService;
    }

    /* ==================== Apple ID ==================== */

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
    public Result<AppleAccount> saveAccount(@RequestBody AppleRequest req) {
        return Result.ok(service.saveAccount(req.getAppleId(), req.getNote(), currentUser()));
    }

    @DeleteMapping("/accounts/{id}")
    public Result<Void> deleteAccount(@PathVariable Long id) {
        service.deleteAccount(id);
        return Result.ok();
    }

    /* ==================== 子模块一：转发邮箱 ==================== */

    @GetMapping("/forward-emails")
    public Result<Map<String, Object>> listForwardEmails(
            @RequestParam(required = false) Long appleAccountId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(toPageData(service.listForwardEmails(appleAccountId, keyword, page, size), page, size));
    }

    /** 某个 Apple ID 的全部转发邮箱，当前的排在最前（隐藏邮箱录入时的下拉用）。 */
    @GetMapping("/forward-emails/of/{appleAccountId}")
    public Result<List<AppleForwardEmail>> listForwardEmailsOf(@PathVariable Long appleAccountId) {
        return Result.ok(service.listForwardEmailsOf(appleAccountId));
    }

    @PostMapping("/forward-emails")
    public Result<AppleForwardEmail> saveForwardEmail(@RequestBody AppleRequest req) {
        return Result.ok(service.saveForwardEmail(
                req.getAppleAccountId(), req.getAppleId(), req.getForwardEmail(),
                req.getNote(), Boolean.TRUE.equals(req.getSetCurrent()), currentUser()));
    }

    /** 设为当前转发邮箱（同一 Apple ID 下唯一）。 */
    @PostMapping("/forward-emails/{id}/set-current")
    public Result<AppleForwardEmail> setCurrentForward(@PathVariable Long id) {
        return Result.ok(service.setCurrentForward(id));
    }

    @PutMapping("/forward-emails/{id}")
    public Result<AppleForwardEmail> updateForwardEmail(@PathVariable Long id,
                                                        @RequestBody AppleForwardEmail patch) {
        return Result.ok(service.updateForwardEmail(id, patch));
    }

    @DeleteMapping("/forward-emails/{id}")
    public Result<Void> deleteForwardEmail(@PathVariable Long id) {
        service.deleteForwardEmail(id);
        return Result.ok();
    }

    /* ==================== 子模块二：隐藏邮箱 ==================== */

    @GetMapping("/hide-emails")
    public Result<Map<String, Object>> listHideEmails(
            @RequestParam(required = false) Long appleAccountId,
            @RequestParam(required = false) Long forwardEmailId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(toPageData(
                service.listHideEmails(appleAccountId, forwardEmailId, keyword, page, size), page, size));
    }

    @PostMapping("/hide-emails")
    public Result<AppleHideEmail> saveHideEmail(@RequestBody AppleRequest req) {
        return Result.ok(service.saveHideEmail(
                req.getAppleAccountId(), req.getAppleId(), req.getForwardEmailId(),
                req.getHideEmail(), currentUser()));
    }

    /**
     * 批量录入隐藏邮箱。返回 saved / skipped 与入库明细；
     * 已存在或批次内重复的一律跳过，不影响其余记录。
     */
    @PostMapping("/hide-emails/batch")
    public Result<Map<String, Object>> saveHideEmailBatch(@RequestBody AppleRequest req) {
        List<String> wanted = req.getHideEmails() == null ? List.of() : req.getHideEmails();
        List<AppleHideEmail> saved = service.saveHideEmailBatch(
                req.getAppleAccountId(), req.getAppleId(), req.getForwardEmailId(), wanted, currentUser());
        return Result.ok(batchResult(saved, wanted.size(), saved.size()));
    }

    @PutMapping("/hide-emails/{id}")
    public Result<AppleHideEmail> updateHideEmail(@PathVariable Long id, @RequestBody AppleHideEmail patch) {
        return Result.ok(service.updateHideEmail(id, patch));
    }

    @DeleteMapping("/hide-emails/{id}")
    public Result<Void> deleteHideEmail(@PathVariable Long id) {
        service.deleteHideEmail(id);
        return Result.ok();
    }

    /* ==================== 子模块三：黑客邮箱 ==================== */

    @GetMapping("/hacker-emails")
    public Result<Map<String, Object>> listHackerEmails(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(toPageData(service.listHackerEmails(keyword, page, size), page, size));
    }

    @PostMapping("/hacker-emails")
    public Result<AppleHackerEmail> saveHackerEmail(@RequestBody AppleRequest req) {
        return Result.ok(service.saveHackerEmail(
                req.getHideEmailId(), req.getHideEmail(), req.getHackerEmail(), currentUser()));
    }

    /** 批量绑定「隐藏邮箱 → 黑客邮箱」。隐藏邮箱未录入、任一端已占用的跳过。 */
    @PostMapping("/hacker-emails/batch")
    public Result<Map<String, Object>> saveHackerEmailBatch(@RequestBody AppleRequest req) {
        List<AppleRequest> items = req.getItems() == null ? List.of() : req.getItems();
        List<AppleHackerEmail> entities = items.stream().map(i -> {
            AppleHackerEmail e = new AppleHackerEmail();
            e.setHideEmail(i.getHideEmail());
            e.setHackerEmail(i.getHackerEmail());
            return e;
        }).toList();
        List<AppleHackerEmail> saved = service.saveHackerEmailBatch(entities, currentUser());
        return Result.ok(batchResult(saved, items.size(), saved.size()));
    }

    @PutMapping("/hacker-emails/{id}")
    public Result<AppleHackerEmail> updateHackerEmail(@PathVariable Long id, @RequestBody AppleHackerEmail patch) {
        return Result.ok(service.updateHackerEmail(id, patch));
    }

    @DeleteMapping("/hacker-emails/{id}")
    public Result<Void> deleteHackerEmail(@PathVariable Long id) {
        service.deleteHackerEmail(id);
        return Result.ok();
    }

    /* ==================== 模块四 / 五：倒推接码 ==================== */

    /**
     * 倒推接码：entryType=hide 传隐藏邮箱、hacker 传黑客邮箱。
     * 当前转发邮箱接不到码时自动换该 Apple ID 下的其他转发邮箱重试，成功则把它设为当前。
     */
    @PostMapping("/code/fetch")
    public Result<Map<String, Object>> fetchCode(@RequestBody AppleRequest req) {
        String entryType = AppleCodeService.ENTRY_HACKER.equals(req.getEntryType())
                ? AppleCodeService.ENTRY_HACKER : AppleCodeService.ENTRY_HIDE;
        String entry = AppleCodeService.ENTRY_HACKER.equals(entryType)
                ? req.getHackerEmail() : req.getHideEmail();
        int limit = req.getLimit() == null ? 10 : req.getLimit();
        return Result.ok(codeService.fetchCode(entryType, entry, limit, currentUser()));
    }

    /** 检测转发邮箱能否正常取件。不传 id 则检测所有「当前转发邮箱」。 */
    @PostMapping("/code/check")
    public Result<List<Map<String, Object>>> checkForward(@RequestBody(required = false) AppleRequest req) {
        Long id = req == null ? null : req.getForwardEmailId();
        if (id != null) {
            return Result.ok(List.of(codeService.checkForward(id, currentUser())));
        }
        return Result.ok(codeService.checkAllCurrent(currentUser()));
    }

    /** 接码 / 切换 / 检测日志。 */
    @GetMapping("/code/logs")
    public Result<Map<String, Object>> listLogs(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(toPageData(codeService.listLogs(type, success, keyword, page, size), page, size));
    }

    /* ==================== 内部 ==================== */

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

    private Map<String, Object> batchResult(List<?> records, int requested, int saved) {
        Map<String, Object> data = new HashMap<>();
        data.put("saved", saved);
        data.put("skipped", requested - saved);
        data.put("records", records);
        return data;
    }
}
