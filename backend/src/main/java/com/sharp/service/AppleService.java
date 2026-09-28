package com.sharp.service;

import com.sharp.entity.AppleAccount;
import com.sharp.entity.AppleForwardEmail;
import com.sharp.entity.AppleHackerEmail;
import com.sharp.entity.AppleHideEmail;
import com.sharp.repository.AppleAccountRepository;
import com.sharp.repository.AppleForwardEmailRepository;
import com.sharp.repository.AppleHackerEmailRepository;
import com.sharp.repository.AppleHideEmailRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Apple 录入的三个子模块：
 * 一、Apple ID 与其转发邮箱（可多个，其中一个为当前）
 * 二、Apple ID 与隐藏邮箱（记录走的是哪个转发邮箱）
 * 三、隐藏邮箱与黑客邮箱（一对一）
 */
@Service
public class AppleService {

    private final AppleAccountRepository accountRepository;
    private final AppleForwardEmailRepository forwardEmailRepository;
    private final AppleHideEmailRepository hideEmailRepository;
    private final AppleHackerEmailRepository hackerEmailRepository;

    public AppleService(AppleAccountRepository accountRepository,
                        AppleForwardEmailRepository forwardEmailRepository,
                        AppleHideEmailRepository hideEmailRepository,
                        AppleHackerEmailRepository hackerEmailRepository) {
        this.accountRepository = accountRepository;
        this.forwardEmailRepository = forwardEmailRepository;
        this.hideEmailRepository = hideEmailRepository;
        this.hackerEmailRepository = hackerEmailRepository;
    }

    /* ==================== Apple ID ==================== */

    public Page<AppleAccount> listAccounts(String keyword, int page, int size) {
        // 前端页码从 1 开始
        int pageIndex = Math.max(page - 1, 0);
        return accountRepository.search(keyword, PageRequest.of(pageIndex, size));
    }

    /**
     * 新增 Apple ID。幂等：账号已存在则补上备注后返回已有那条，
     * 这样前端「新增」与「从已有选」殊途同归，不会因重复录入报错。
     */
    public AppleAccount saveAccount(String appleId, String note, String createdBy) {
        String id = appleId == null ? "" : appleId.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Apple ID 不能为空");
        }
        AppleAccount account = accountRepository.findByAppleId(id).orElseGet(() -> {
            AppleAccount a = new AppleAccount();
            a.setAppleId(id);
            a.setCreatedBy(createdBy);
            return a;
        });
        if (note != null && !note.isBlank()) {
            account.setNote(note.trim());
        }
        return accountRepository.save(account);
    }

    /** 删除 Apple ID。下面还挂着转发邮箱或隐藏邮箱时拒绝，避免留下孤儿数据。 */
    public void deleteAccount(Long id) {
        long hides = hideEmailRepository.countByAppleAccountId(id);
        if (hides > 0) {
            throw new IllegalArgumentException("该 Apple ID 下还有 " + hides + " 个隐藏邮箱，请先删除它们");
        }
        long forwards = forwardEmailRepository.countByAppleAccountId(id);
        if (forwards > 0) {
            throw new IllegalArgumentException("该 Apple ID 下还有 " + forwards + " 个转发邮箱，请先删除它们");
        }
        accountRepository.deleteById(id);
    }

    /* ==================== 子模块一：转发邮箱 ==================== */

    public Page<AppleForwardEmail> listForwardEmails(Long appleAccountId, String keyword, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);
        Page<AppleForwardEmail> result =
                forwardEmailRepository.search(appleAccountId, keyword, PageRequest.of(pageIndex, size));
        fillForwardExtras(result.getContent());
        return result;
    }

    /** 某个 Apple ID 的全部转发邮箱，当前的排在最前（供隐藏邮箱录入时的下拉）。 */
    public List<AppleForwardEmail> listForwardEmailsOf(Long appleAccountId) {
        List<AppleForwardEmail> rows =
                forwardEmailRepository.findByAppleAccountIdOrderByIsCurrentDescIdDesc(appleAccountId);
        fillForwardExtras(rows);
        return rows;
    }

    /**
     * 录入转发邮箱。同一 Apple ID 下地址重复则复用原记录（只更新备注），
     * 与 saveAccount 的幂等策略保持一致。setCurrent 为 true 时顺带设为当前。
     */
    @Transactional
    public AppleForwardEmail saveForwardEmail(Long appleAccountId, String appleId, String forwardEmail,
                                              String note, boolean setCurrent, String createdBy) {
        AppleAccount account = resolveAccount(appleAccountId, appleId, createdBy);
        String email = forwardEmail == null ? "" : forwardEmail.trim();
        if (email.isEmpty()) {
            throw new IllegalArgumentException("转发邮箱不能为空");
        }

        AppleForwardEmail entity = forwardEmailRepository
                .findByAppleAccountIdAndForwardEmail(account.getId(), email)
                .orElseGet(() -> {
                    AppleForwardEmail f = new AppleForwardEmail();
                    f.setAppleAccountId(account.getId());
                    f.setForwardEmail(email);
                    f.setCreatedBy(createdBy);
                    // 该 Apple ID 的第一个转发邮箱直接就是当前
                    f.setIsCurrent(forwardEmailRepository.countByAppleAccountId(account.getId()) == 0);
                    return f;
                });
        if (note != null && !note.isBlank()) {
            entity.setNote(note.trim());
        }
        if (setCurrent) {
            forwardEmailRepository.clearCurrent(account.getId());
            entity.setIsCurrent(true);
        }
        AppleForwardEmail saved = forwardEmailRepository.save(entity);
        fillForwardExtras(List.of(saved));
        return saved;
    }

    /** 设为当前：同一 Apple ID 下先全部清零，再置位这一条。 */
    @Transactional
    public AppleForwardEmail setCurrentForward(Long id) {
        AppleForwardEmail entity = forwardEmailRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("转发邮箱不存在：" + id));
        forwardEmailRepository.clearCurrent(entity.getAppleAccountId());
        entity.setIsCurrent(true);
        AppleForwardEmail saved = forwardEmailRepository.save(entity);
        fillForwardExtras(List.of(saved));
        return saved;
    }

    /** 局部更新转发邮箱：只改传进来的字段。 */
    @Transactional
    public AppleForwardEmail updateForwardEmail(Long id, AppleForwardEmail patch) {
        AppleForwardEmail exist = forwardEmailRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("转发邮箱不存在：" + id));
        if (patch.getForwardEmail() != null) {
            String email = patch.getForwardEmail().trim();
            if (email.isEmpty()) {
                throw new IllegalArgumentException("转发邮箱不能为空");
            }
            forwardEmailRepository.findByAppleAccountIdAndForwardEmail(exist.getAppleAccountId(), email)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new IllegalArgumentException("该 Apple ID 下已有这个转发邮箱（#" + other.getId() + "）");
                    });
            exist.setForwardEmail(email);
        }
        if (patch.getNote() != null) {
            exist.setNote(trimToNull(patch.getNote()));
        }
        if (Boolean.TRUE.equals(patch.getIsCurrent())) {
            forwardEmailRepository.clearCurrent(exist.getAppleAccountId());
            exist.setIsCurrent(true);
        }
        AppleForwardEmail saved = forwardEmailRepository.save(exist);
        fillForwardExtras(List.of(saved));
        return saved;
    }

    /** 删除转发邮箱。仍被隐藏邮箱引用时拒绝。 */
    public void deleteForwardEmail(Long id) {
        long used = hideEmailRepository.countByForwardEmailId(id);
        if (used > 0) {
            throw new IllegalArgumentException("该转发邮箱下还有 " + used + " 个隐藏邮箱，请先处理它们");
        }
        forwardEmailRepository.deleteById(id);
    }

    /* ==================== 子模块二：隐藏邮箱 ==================== */

    public Page<AppleHideEmail> listHideEmails(Long appleAccountId, Long forwardEmailId,
                                               String keyword, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);
        Page<AppleHideEmail> result = hideEmailRepository.search(
                appleAccountId, forwardEmailId, keyword, PageRequest.of(pageIndex, size));
        fillHideExtras(result.getContent());
        return result;
    }

    /**
     * 录入一条隐藏邮箱。appleAccountId 与 appleId 给其一；
     * forwardEmailId 不传时自动挂到该 Apple ID 的当前转发邮箱上。
     */
    public AppleHideEmail saveHideEmail(Long appleAccountId, String appleId, Long forwardEmailId,
                                        String hideEmail, String createdBy) {
        AppleAccount account = resolveAccount(appleAccountId, appleId, createdBy);
        String hide = hideEmail == null ? "" : hideEmail.trim();
        if (hide.isEmpty()) {
            throw new IllegalArgumentException("隐藏邮箱不能为空");
        }
        hideEmailRepository.findByHideEmail(hide).ifPresent(exist -> {
            throw new IllegalArgumentException("隐藏邮箱已存在（#" + exist.getId() + "），请勿重复录入");
        });

        AppleHideEmail entity = new AppleHideEmail();
        entity.setAppleAccountId(account.getId());
        entity.setForwardEmailId(resolveForwardEmailId(account.getId(), forwardEmailId));
        entity.setHideEmail(hide);
        entity.setCreatedBy(createdBy);
        AppleHideEmail saved = hideEmailRepository.save(entity);
        fillHideExtras(List.of(saved));
        return saved;
    }

    /**
     * 批量录入同一个 Apple ID 下的多条隐藏邮箱。
     * 已存在的、以及本批次内自身重复的，都跳过而非报错。
     */
    public List<AppleHideEmail> saveHideEmailBatch(Long appleAccountId, String appleId, Long forwardEmailId,
                                                   List<String> hideEmails, String createdBy) {
        if (hideEmails == null || hideEmails.isEmpty()) {
            throw new IllegalArgumentException("没有可录入的记录");
        }
        AppleAccount account = resolveAccount(appleAccountId, appleId, createdBy);
        Long forwardId = resolveForwardEmailId(account.getId(), forwardEmailId);

        List<String> wanted = hideEmails.stream()
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        // 一次查出库里已占用的地址，避免逐条查
        Set<String> taken = hideEmailRepository.findByHideEmailIn(wanted).stream()
                .map(AppleHideEmail::getHideEmail)
                .collect(Collectors.toCollection(HashSet::new));

        List<AppleHideEmail> toSave = new ArrayList<>();
        for (String hide : wanted) {
            if (!taken.add(hide)) {
                continue;
            }
            AppleHideEmail entity = new AppleHideEmail();
            entity.setAppleAccountId(account.getId());
            entity.setForwardEmailId(forwardId);
            entity.setHideEmail(hide);
            entity.setCreatedBy(createdBy);
            toSave.add(entity);
        }
        List<AppleHideEmail> saved = hideEmailRepository.saveAll(toSave);
        fillHideExtras(saved);
        return saved;
    }

    /** 局部更新隐藏邮箱：只改传进来的字段。 */
    public AppleHideEmail updateHideEmail(Long id, AppleHideEmail patch) {
        AppleHideEmail exist = hideEmailRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在：" + id));

        if (patch.getHideEmail() != null) {
            String hide = patch.getHideEmail().trim();
            if (hide.isEmpty()) {
                throw new IllegalArgumentException("隐藏邮箱不能为空");
            }
            hideEmailRepository.findByHideEmail(hide)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new IllegalArgumentException("隐藏邮箱已存在（#" + other.getId() + "）");
                    });
            exist.setHideEmail(hide);
        }
        if (patch.getForwardEmailId() != null) {
            exist.setForwardEmailId(patch.getForwardEmailId());
        }
        if (patch.getAppleAccountId() != null) {
            exist.setAppleAccountId(patch.getAppleAccountId());
        }
        AppleHideEmail saved = hideEmailRepository.save(exist);
        fillHideExtras(List.of(saved));
        return saved;
    }

    /** 删除隐藏邮箱。已绑黑客邮箱时拒绝，先去子模块三解绑。 */
    public void deleteHideEmail(Long id) {
        hackerEmailRepository.findByHideEmailId(id).ifPresent(k -> {
            throw new IllegalArgumentException("该隐藏邮箱已绑定黑客邮箱（" + k.getHackerEmail() + "），请先解绑");
        });
        hideEmailRepository.deleteById(id);
    }

    /* ==================== 子模块三：黑客邮箱 ==================== */

    public Page<AppleHackerEmail> listHackerEmails(String keyword, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);
        Page<AppleHackerEmail> result = hackerEmailRepository.search(keyword, PageRequest.of(pageIndex, size));
        fillHackerExtras(result.getContent());
        return result;
    }

    /** 绑定黑客邮箱到隐藏邮箱。一对一：两端都不能已被占用。 */
    public AppleHackerEmail saveHackerEmail(Long hideEmailId, String hideEmail, String hackerEmail, String createdBy) {
        AppleHideEmail hide = resolveHideEmail(hideEmailId, hideEmail);
        String hacker = hackerEmail == null ? "" : hackerEmail.trim();
        if (hacker.isEmpty()) {
            throw new IllegalArgumentException("黑客邮箱不能为空");
        }
        hackerEmailRepository.findByHideEmailId(hide.getId()).ifPresent(k -> {
            throw new IllegalArgumentException("该隐藏邮箱已绑定 " + k.getHackerEmail() + "，请先解绑或直接编辑");
        });
        hackerEmailRepository.findByHackerEmail(hacker).ifPresent(k -> {
            throw new IllegalArgumentException("该黑客邮箱已绑定其他隐藏邮箱（#" + k.getHideEmailId() + "）");
        });

        AppleHackerEmail entity = new AppleHackerEmail();
        entity.setHideEmailId(hide.getId());
        entity.setHackerEmail(hacker);
        entity.setCreatedBy(createdBy);
        AppleHackerEmail saved = hackerEmailRepository.save(entity);
        fillHackerExtras(List.of(saved));
        return saved;
    }

    /**
     * 批量绑定：每项一对「隐藏邮箱 → 黑客邮箱」。
     * 隐藏邮箱不存在、任一端已被占用、或批次内重复的，都跳过。
     */
    public List<AppleHackerEmail> saveHackerEmailBatch(List<AppleHackerEmail> items, String createdBy) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("没有可录入的记录");
        }
        // 隐藏邮箱地址 → 记录
        List<String> hideAddrs = items.stream()
                .map(i -> i.getHideEmail() == null ? "" : i.getHideEmail().trim())
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        Map<String, AppleHideEmail> hideByAddr = hideEmailRepository.findByHideEmailIn(hideAddrs).stream()
                .collect(Collectors.toMap(AppleHideEmail::getHideEmail, h -> h, (a, b) -> a));

        Set<Long> boundHideIds = hackerEmailRepository
                .findByHideEmailIdIn(hideByAddr.values().stream().map(AppleHideEmail::getId).toList())
                .stream().map(AppleHackerEmail::getHideEmailId).collect(Collectors.toCollection(HashSet::new));
        List<String> hackerAddrs = items.stream()
                .map(i -> i.getHackerEmail() == null ? "" : i.getHackerEmail().trim())
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        Set<String> takenHackers = hackerEmailRepository.findByHackerEmailIn(hackerAddrs).stream()
                .map(AppleHackerEmail::getHackerEmail).collect(Collectors.toCollection(HashSet::new));

        List<AppleHackerEmail> toSave = new ArrayList<>();
        for (AppleHackerEmail item : items) {
            String hideAddr = item.getHideEmail() == null ? "" : item.getHideEmail().trim();
            String hacker = item.getHackerEmail() == null ? "" : item.getHackerEmail().trim();
            AppleHideEmail hide = hideByAddr.get(hideAddr);
            if (hide == null || hacker.isEmpty()) {
                continue;                       // 隐藏邮箱没录过 / 黑客邮箱为空
            }
            if (!boundHideIds.add(hide.getId()) || !takenHackers.add(hacker)) {
                continue;                       // 任一端已被占用（含本批次内重复）
            }
            AppleHackerEmail entity = new AppleHackerEmail();
            entity.setHideEmailId(hide.getId());
            entity.setHackerEmail(hacker);
            entity.setCreatedBy(createdBy);
            toSave.add(entity);
        }
        List<AppleHackerEmail> saved = hackerEmailRepository.saveAll(toSave);
        fillHackerExtras(saved);
        return saved;
    }

    /** 局部更新绑定：改黑客邮箱地址，或改绑到另一个隐藏邮箱。 */
    public AppleHackerEmail updateHackerEmail(Long id, AppleHackerEmail patch) {
        AppleHackerEmail exist = hackerEmailRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在：" + id));
        if (patch.getHackerEmail() != null) {
            String hacker = patch.getHackerEmail().trim();
            if (hacker.isEmpty()) {
                throw new IllegalArgumentException("黑客邮箱不能为空");
            }
            hackerEmailRepository.findByHackerEmail(hacker)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new IllegalArgumentException("该黑客邮箱已绑定其他隐藏邮箱（#" + other.getHideEmailId() + "）");
                    });
            exist.setHackerEmail(hacker);
        }
        if (patch.getHideEmailId() != null) {
            hackerEmailRepository.findByHideEmailId(patch.getHideEmailId())
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new IllegalArgumentException("目标隐藏邮箱已绑定 " + other.getHackerEmail());
                    });
            exist.setHideEmailId(patch.getHideEmailId());
        }
        AppleHackerEmail saved = hackerEmailRepository.save(exist);
        fillHackerExtras(List.of(saved));
        return saved;
    }

    public void deleteHackerEmail(Long id) {
        hackerEmailRepository.deleteById(id);
    }

    /* ==================== 内部 ==================== */

    private String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** 按 id 取账号；没给 id 就按账号名建或复用。 */
    private AppleAccount resolveAccount(Long appleAccountId, String appleId, String createdBy) {
        if (appleAccountId != null) {
            return accountRepository.findById(appleAccountId)
                    .orElseThrow(() -> new IllegalArgumentException("Apple ID 不存在：" + appleAccountId));
        }
        if (appleId == null || appleId.isBlank()) {
            throw new IllegalArgumentException("请先选择或新增 Apple ID");
        }
        return saveAccount(appleId, null, createdBy);
    }

    /** 没指定转发邮箱时挂到该 Apple ID 的当前转发邮箱上；指定了则校验归属。 */
    private Long resolveForwardEmailId(Long appleAccountId, Long forwardEmailId) {
        if (forwardEmailId != null) {
            AppleForwardEmail f = forwardEmailRepository.findById(forwardEmailId)
                    .orElseThrow(() -> new IllegalArgumentException("转发邮箱不存在：" + forwardEmailId));
            if (!f.getAppleAccountId().equals(appleAccountId)) {
                throw new IllegalArgumentException("该转发邮箱不属于所选的 Apple ID");
            }
            return f.getId();
        }
        return forwardEmailRepository.findByAppleAccountIdOrderByIsCurrentDescIdDesc(appleAccountId).stream()
                .filter(f -> Boolean.TRUE.equals(f.getIsCurrent()))
                .map(AppleForwardEmail::getId)
                .findFirst()
                .orElse(null);
    }

    /** 按 id 或地址取隐藏邮箱。 */
    private AppleHideEmail resolveHideEmail(Long hideEmailId, String hideEmail) {
        if (hideEmailId != null) {
            return hideEmailRepository.findById(hideEmailId)
                    .orElseThrow(() -> new IllegalArgumentException("隐藏邮箱不存在：" + hideEmailId));
        }
        String addr = hideEmail == null ? "" : hideEmail.trim();
        if (addr.isEmpty()) {
            throw new IllegalArgumentException("请先选择隐藏邮箱");
        }
        return hideEmailRepository.findByHideEmail(addr)
                .orElseThrow(() -> new IllegalArgumentException("隐藏邮箱尚未录入：" + addr));
    }

    /* —— 列表展示字段的批量回填，避免逐条查库 —— */

    private Map<Long, String> appleIdsOf(List<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return accountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(AppleAccount::getId, AppleAccount::getAppleId));
    }

    private void fillForwardExtras(List<AppleForwardEmail> rows) {
        if (rows.isEmpty()) {
            return;
        }
        Map<Long, String> accounts = appleIdsOf(rows.stream()
                .map(AppleForwardEmail::getAppleAccountId).distinct().toList());
        rows.forEach(r -> {
            r.setAppleId(accounts.get(r.getAppleAccountId()));
            r.setHideEmailCount(hideEmailRepository.countByForwardEmailId(r.getId()));
        });
    }

    private void fillHideExtras(List<AppleHideEmail> rows) {
        if (rows.isEmpty()) {
            return;
        }
        Map<Long, String> accounts = appleIdsOf(rows.stream()
                .map(AppleHideEmail::getAppleAccountId).distinct().toList());
        List<Long> forwardIds = rows.stream()
                .map(AppleHideEmail::getForwardEmailId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> forwards = forwardIds.isEmpty() ? Map.of()
                : forwardEmailRepository.findAllById(forwardIds).stream()
                .collect(Collectors.toMap(AppleForwardEmail::getId, AppleForwardEmail::getForwardEmail));
        Map<Long, String> hackers = hackerEmailRepository
                .findByHideEmailIdIn(rows.stream().map(AppleHideEmail::getId).toList()).stream()
                .collect(Collectors.toMap(AppleHackerEmail::getHideEmailId, AppleHackerEmail::getHackerEmail));

        rows.forEach(r -> {
            r.setAppleId(accounts.get(r.getAppleAccountId()));
            r.setForwardEmail(r.getForwardEmailId() == null ? null : forwards.get(r.getForwardEmailId()));
            r.setHackerEmail(hackers.get(r.getId()));
        });
    }

    private void fillHackerExtras(List<AppleHackerEmail> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<AppleHideEmail> hides = hideEmailRepository.findAllById(
                rows.stream().map(AppleHackerEmail::getHideEmailId).distinct().toList());
        Map<Long, AppleHideEmail> hideById = hides.stream()
                .collect(Collectors.toMap(AppleHideEmail::getId, h -> h));
        Map<Long, String> accounts = appleIdsOf(hides.stream()
                .map(AppleHideEmail::getAppleAccountId).distinct().toList());

        rows.forEach(r -> {
            AppleHideEmail hide = hideById.get(r.getHideEmailId());
            r.setHideEmail(hide == null ? null : hide.getHideEmail());
            r.setAppleId(hide == null ? null : accounts.get(hide.getAppleAccountId()));
        });
    }
}
