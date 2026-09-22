package com.sharp.service;

import com.sharp.entity.AppleAccount;
import com.sharp.entity.AppleHideEmail;
import com.sharp.repository.AppleAccountRepository;
import com.sharp.repository.AppleHideEmailRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Apple ID 与隐藏邮箱的录入 / 查询。
 * 录入流程：选或建 Apple ID → 录隐藏邮箱 → 录重定向邮箱。
 */
@Service
public class AppleService {

    private final AppleAccountRepository accountRepository;
    private final AppleHideEmailRepository hideEmailRepository;

    public AppleService(AppleAccountRepository accountRepository,
                        AppleHideEmailRepository hideEmailRepository) {
        this.accountRepository = accountRepository;
        this.hideEmailRepository = hideEmailRepository;
    }

    /* ---------- Apple ID ---------- */

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

    /** 删除 Apple ID。下面还挂着隐藏邮箱时拒绝，避免留下孤儿数据。 */
    public void deleteAccount(Long id) {
        long children = hideEmailRepository.countByAppleAccountId(id);
        if (children > 0) {
            throw new IllegalArgumentException("该 Apple ID 下还有 " + children + " 个隐藏邮箱，请先删除它们");
        }
        accountRepository.deleteById(id);
    }

    /* ---------- 隐藏邮箱 ---------- */

    /** 分页查隐藏邮箱，并回填所属 Apple ID 账号供列表展示。 */
    public Page<AppleHideEmail> listHideEmails(Long appleAccountId, String keyword, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);
        Page<AppleHideEmail> result =
                hideEmailRepository.search(appleAccountId, keyword, PageRequest.of(pageIndex, size));
        fillAppleId(result.getContent());
        return result;
    }

    /**
     * 录入一条隐藏邮箱。appleAccountId 与 appleId 给其一：
     * 传 id 用已有账号，只传账号名则顺手建（或复用）一个。
     * HackerOne 邮箱与谷歌别名邮箱同属一条记录，缺一个也允许存（前端会先拦）。
     */
    public AppleHideEmail saveHideEmail(Long appleAccountId, String appleId, String hideEmail,
                                        String hackerOneEmail, String googleAliasEmail, String createdBy) {
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
        entity.setHideEmail(hide);
        entity.setRedirectEmail(trimToNull(hackerOneEmail));
        entity.setGoogleAliasEmail(trimToNull(googleAliasEmail));
        entity.setCreatedBy(createdBy);
        AppleHideEmail saved = hideEmailRepository.save(entity);
        saved.setAppleId(account.getAppleId());
        return saved;
    }

    /**
     * 局部更新一条隐藏邮箱：只改传进来的字段，没传的保持原值
     * （整体 save 会把漏传的字段清成 null）。空字符串表示清空该字段。
     */
    public AppleHideEmail updateHideEmail(Long id, AppleHideEmail patch) {
        AppleHideEmail exist = hideEmailRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("记录不存在：" + id));

        if (patch.getHideEmail() != null) {
            String hide = patch.getHideEmail().trim();
            if (hide.isEmpty()) {
                throw new IllegalArgumentException("隐藏邮箱不能为空");
            }
            // 换成别人已占用的地址要拦下来
            hideEmailRepository.findByHideEmail(hide)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new IllegalArgumentException("隐藏邮箱已存在（#" + other.getId() + "）");
                    });
            exist.setHideEmail(hide);
        }
        if (patch.getRedirectEmail() != null) {
            exist.setRedirectEmail(trimToNull(patch.getRedirectEmail()));
        }
        if (patch.getGoogleAliasEmail() != null) {
            exist.setGoogleAliasEmail(trimToNull(patch.getGoogleAliasEmail()));
        }
        if (patch.getAppleAccountId() != null) {
            exist.setAppleAccountId(patch.getAppleAccountId());
        }
        AppleHideEmail saved = hideEmailRepository.save(exist);
        fillAppleId(List.of(saved));
        return saved;
    }

    public void deleteHideEmail(Long id) {
        hideEmailRepository.deleteById(id);
    }

    /* ---------- 内部 ---------- */

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

    /** 批量回填 appleId，避免逐条查库。 */
    private void fillAppleId(List<AppleHideEmail> rows) {
        if (rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream().map(AppleHideEmail::getAppleAccountId).distinct().toList();
        Map<Long, String> idToAccount = accountRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(AppleAccount::getId, AppleAccount::getAppleId));
        rows.forEach(r -> r.setAppleId(idToAccount.get(r.getAppleAccountId())));
    }
}
