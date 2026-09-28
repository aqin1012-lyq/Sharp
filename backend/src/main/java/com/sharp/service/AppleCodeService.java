package com.sharp.service;

import com.sharp.dto.MailMessageDto;
import com.sharp.entity.AppleCodeLog;
import com.sharp.entity.AppleForwardEmail;
import com.sharp.entity.AppleHackerEmail;
import com.sharp.entity.AppleHideEmail;
import com.sharp.entity.EmailAccount;
import com.sharp.repository.AppleAccountRepository;
import com.sharp.repository.AppleCodeLogRepository;
import com.sharp.repository.AppleForwardEmailRepository;
import com.sharp.repository.AppleHackerEmailRepository;
import com.sharp.repository.AppleHideEmailRepository;
import com.sharp.repository.EmailAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 模块四 / 五：从隐藏邮箱或黑客邮箱倒推到转发邮箱，用该转发邮箱对应的已录入账号接码。
 *
 * 链路：黑客邮箱 →（一对一）隐藏邮箱 → Apple ID → 当前转发邮箱 → email_account → 取件。
 * 当前转发邮箱接不到码时，自动改用该 Apple ID 下的其他转发邮箱重试，成功则把它设为当前。
 * 接码 / 切换 / 检测三类事件都写进 apple_code_log。
 */
@Service
public class AppleCodeService {

    /** 入口类型 */
    public static final String ENTRY_HIDE = "hide";
    public static final String ENTRY_HACKER = "hacker";

    private final AppleAccountRepository accountRepository;
    private final AppleForwardEmailRepository forwardEmailRepository;
    private final AppleHideEmailRepository hideEmailRepository;
    private final AppleHackerEmailRepository hackerEmailRepository;
    private final AppleCodeLogRepository logRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final MailFetchService mailFetchService;
    private final AppleService appleService;

    public AppleCodeService(AppleAccountRepository accountRepository,
                            AppleForwardEmailRepository forwardEmailRepository,
                            AppleHideEmailRepository hideEmailRepository,
                            AppleHackerEmailRepository hackerEmailRepository,
                            AppleCodeLogRepository logRepository,
                            EmailAccountRepository emailAccountRepository,
                            MailFetchService mailFetchService,
                            AppleService appleService) {
        this.accountRepository = accountRepository;
        this.forwardEmailRepository = forwardEmailRepository;
        this.hideEmailRepository = hideEmailRepository;
        this.hackerEmailRepository = hackerEmailRepository;
        this.logRepository = logRepository;
        this.emailAccountRepository = emailAccountRepository;
        this.mailFetchService = mailFetchService;
        this.appleService = appleService;
    }

    /**
     * 倒推接码。entryType 为 hide（隐藏邮箱）或 hacker（黑客邮箱）。
     * 返回链路说明、邮件列表、验证码，以及是否发生过自动切换。
     */
    public Map<String, Object> fetchCode(String entryType, String entryEmail, int limit, String operator) {
        String entry = entryEmail == null ? "" : entryEmail.trim();
        if (entry.isEmpty()) {
            throw new IllegalArgumentException("请先填写" + (ENTRY_HACKER.equals(entryType) ? "黑客邮箱" : "隐藏邮箱"));
        }

        AppleHideEmail hide = resolveHide(entryType, entry);
        String appleId = accountRepository.findById(hide.getAppleAccountId())
                .map(a -> a.getAppleId()).orElse(null);

        // 当前转发邮箱排在最前，其余作为失效时的备选
        List<AppleForwardEmail> candidates =
                forwardEmailRepository.findByAppleAccountIdOrderByIsCurrentDescIdDesc(hide.getAppleAccountId());
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("该 Apple ID（" + appleId + "）还没录转发邮箱，无法接码");
        }

        List<Map<String, Object>> attempts = new ArrayList<>();
        AppleForwardEmail original = candidates.get(0);

        for (AppleForwardEmail forward : candidates) {
            Attempt attempt = tryFetch(hide, forward, entry, limit, operator);
            attempts.add(attempt.describe());
            if (!attempt.success) {
                continue;
            }
            boolean switched = !forward.getId().equals(original.getId());
            if (switched) {
                // 换到能用的这个，并记一条切换日志
                appleService.setCurrentForward(forward.getId());
                log(AppleCodeLog.TYPE_SWITCH, entry, hide, forward, true, null,
                        "原转发邮箱 " + original.getForwardEmail() + " 接不到码，已切换到 " + forward.getForwardEmail(),
                        null, operator);
            }
            return result(entryType, entry, appleId, hide, forward, attempt, switched, original, attempts);
        }

        // 全部试过仍拿不到
        Map<String, Object> out = baseInfo(entryType, entry, appleId, hide, null);
        out.put("success", false);
        out.put("switched", false);
        out.put("messages", List.of());
        out.put("attempts", attempts);
        out.put("message", "该 Apple ID 下 " + candidates.size() + " 个转发邮箱都没能接到验证码");
        return out;
    }

    /** 检测某个转发邮箱当前能否正常取件（不要求有验证码）。 */
    public Map<String, Object> checkForward(Long forwardEmailId, String operator) {
        AppleForwardEmail forward = forwardEmailRepository.findById(forwardEmailId)
                .orElseThrow(() -> new IllegalArgumentException("转发邮箱不存在：" + forwardEmailId));
        return doCheck(forward, operator);
    }

    /** 一键检测：对所有「当前转发邮箱」各跑一次。 */
    public List<Map<String, Object>> checkAllCurrent(String operator) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AppleForwardEmail forward : forwardEmailRepository.findAll()) {
            if (Boolean.TRUE.equals(forward.getIsCurrent())) {
                out.add(doCheck(forward, operator));
            }
        }
        return out;
    }

    public Page<AppleCodeLog> listLogs(String type, Boolean success, String keyword, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);
        Page<AppleCodeLog> result = logRepository.search(type, success, keyword, PageRequest.of(pageIndex, size));
        fillAppleId(result.getContent());
        return result;
    }

    /* ==================== 内部 ==================== */

    private Map<String, Object> doCheck(AppleForwardEmail forward, String operator) {
        long start = System.currentTimeMillis();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("forwardEmailId", forward.getId());
        out.put("forwardEmail", forward.getForwardEmail());
        try {
            EmailAccount account = matchAccount(forward.getForwardEmail());
            List<MailMessageDto> messages = mailFetchService.fetchByAccount(account, "all", 3);
            int cost = (int) (System.currentTimeMillis() - start);
            String msg = "取件正常，拉回 " + messages.size() + " 封";
            out.put("success", true);
            out.put("message", msg);
            log(AppleCodeLog.TYPE_CHECK, null, null, forward, true, null, msg, cost, operator);
        } catch (Exception e) {
            int cost = (int) (System.currentTimeMillis() - start);
            out.put("success", false);
            out.put("message", e.getMessage());
            log(AppleCodeLog.TYPE_CHECK, null, null, forward, false, null, e.getMessage(), cost, operator);
        }
        return out;
    }

    /** 用一个转发邮箱试一次取件，无论成败都写 fetch 日志。 */
    private Attempt tryFetch(AppleHideEmail hide, AppleForwardEmail forward,
                             String entry, int limit, String operator) {
        long start = System.currentTimeMillis();
        Attempt attempt = new Attempt();
        attempt.forwardEmail = forward.getForwardEmail();
        try {
            EmailAccount account = matchAccount(forward.getForwardEmail());
            attempt.accountEmail = account.getEmail();
            attempt.accountType = account.getEmailType();
            List<MailMessageDto> messages = mailFetchService.fetchByAccount(account, "all", limit);
            attempt.messages = messages;
            attempt.verifyCode = messages.stream()
                    .map(MailMessageDto::getVerifyCode)
                    .filter(c -> c != null && !c.isBlank())
                    .findFirst()
                    .orElse(null);
            attempt.success = attempt.verifyCode != null;
            attempt.message = attempt.success
                    ? "取到验证码"
                    : "拉回 " + messages.size() + " 封邮件，但没有可提取的验证码";
        } catch (Exception e) {
            attempt.success = false;
            attempt.message = e.getMessage();
        }
        attempt.durationMs = (int) (System.currentTimeMillis() - start);
        log(AppleCodeLog.TYPE_FETCH, entry, hide, forward, attempt.success,
                attempt.verifyCode, attempt.message, attempt.durationMs, operator);
        return attempt;
    }

    /** 黑客邮箱多一跳：先找到绑定的隐藏邮箱。 */
    private AppleHideEmail resolveHide(String entryType, String entry) {
        if (ENTRY_HACKER.equals(entryType)) {
            AppleHackerEmail hacker = hackerEmailRepository.findByHackerEmail(entry)
                    .orElseThrow(() -> new IllegalArgumentException("黑客邮箱尚未录入：" + entry));
            return hideEmailRepository.findById(hacker.getHideEmailId())
                    .orElseThrow(() -> new IllegalArgumentException("该黑客邮箱绑定的隐藏邮箱已不存在"));
        }
        return hideEmailRepository.findByHideEmail(entry)
                .orElseThrow(() -> new IllegalArgumentException("隐藏邮箱尚未录入：" + entry));
    }

    /**
     * 转发邮箱 → 已录入的邮箱账号。先精确匹配；
     * Gmail 的 + 别名（xxx+88@gmail.com）回退到基础账号 xxx@gmail.com。
     */
    private EmailAccount matchAccount(String forwardEmail) {
        String addr = forwardEmail == null ? "" : forwardEmail.trim();
        Optional<EmailAccount> exact = emailAccountRepository.findFirstByEmailIgnoreCase(addr);
        if (exact.isPresent()) {
            return exact.get();
        }
        String base = stripPlusAlias(addr);
        if (!base.equalsIgnoreCase(addr)) {
            Optional<EmailAccount> fallback = emailAccountRepository.findFirstByEmailIgnoreCase(base);
            if (fallback.isPresent()) {
                return fallback.get();
            }
        }
        throw new IllegalArgumentException("转发邮箱 " + addr + " 尚未在「邮箱管理」里录入，无法取件");
    }

    /** xxx+88@gmail.com → xxx@gmail.com */
    private static String stripPlusAlias(String email) {
        int at = email.indexOf('@');
        int plus = email.indexOf('+');
        if (at > 0 && plus > 0 && plus < at) {
            return email.substring(0, plus) + email.substring(at);
        }
        return email;
    }

    private Map<String, Object> baseInfo(String entryType, String entry, String appleId,
                                         AppleHideEmail hide, AppleForwardEmail forward) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("entryType", entryType);
        out.put("entryEmail", entry);
        out.put("appleId", appleId);
        out.put("hideEmail", hide == null ? null : hide.getHideEmail());
        out.put("forwardEmail", forward == null ? null : forward.getForwardEmail());
        return out;
    }

    private Map<String, Object> result(String entryType, String entry, String appleId, AppleHideEmail hide,
                                       AppleForwardEmail forward, Attempt attempt, boolean switched,
                                       AppleForwardEmail original, List<Map<String, Object>> attempts) {
        Map<String, Object> out = baseInfo(entryType, entry, appleId, hide, forward);
        out.put("accountEmail", attempt.accountEmail);
        out.put("accountType", attempt.accountType);
        out.put("success", true);
        out.put("verifyCode", attempt.verifyCode);
        out.put("messages", attempt.messages);
        out.put("switched", switched);
        out.put("previousForwardEmail", switched ? original.getForwardEmail() : null);
        out.put("attempts", attempts);
        out.put("message", attempt.message);
        return out;
    }

    private void log(String type, String entryEmail, AppleHideEmail hide, AppleForwardEmail forward,
                     boolean success, String verifyCode, String message, Integer durationMs, String operator) {
        AppleCodeLog row = new AppleCodeLog();
        row.setType(type);
        row.setEntryEmail(entryEmail);
        row.setHideEmailId(hide == null ? null : hide.getId());
        row.setAppleAccountId(hide != null ? hide.getAppleAccountId()
                : (forward == null ? null : forward.getAppleAccountId()));
        row.setForwardEmailId(forward == null ? null : forward.getId());
        row.setForwardEmail(forward == null ? null : forward.getForwardEmail());
        row.setSuccess(success);
        row.setVerifyCode(verifyCode);
        row.setMessage(trim(message, 1000));
        row.setDurationMs(durationMs);
        row.setCreatedBy(operator);
        logRepository.save(row);
    }

    private void fillAppleId(List<AppleCodeLog> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream().map(AppleCodeLog::getAppleAccountId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> map = accountRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(a -> a.getId(), a -> a.getAppleId()));
        rows.forEach(r -> r.setAppleId(map.get(r.getAppleAccountId())));
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** 一次取件尝试的结果。 */
    private static class Attempt {
        boolean success;
        String forwardEmail;
        String accountEmail;
        String accountType;
        String verifyCode;
        String message;
        Integer durationMs;
        List<MailMessageDto> messages = List.of();

        Map<String, Object> describe() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("forwardEmail", forwardEmail);
            m.put("success", success);
            m.put("message", message);
            m.put("durationMs", durationMs);
            return m;
        }
    }
}
