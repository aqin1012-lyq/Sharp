package com.sharp.service;

import com.sharp.dto.ClaudeDupCheck;
import com.sharp.entity.ClaudeAccount;
import com.sharp.repository.ClaudeAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

@Service
public class ClaudeAccountService {

    private final ClaudeAccountRepository repository;

    /** 默认字段顺序：claude账号 / 邮箱账号 / 邮箱密码 / SK / ID。 */
    private static final List<String> DEFAULT_FIELDS =
            List.of("claudeAccount", "email", "emailPassword", "sk", "accountId");

    public ClaudeAccountService(ClaudeAccountRepository repository) {
        this.repository = repository;
    }

    /**
     * 解析多行原始串。每行按 2+ 连字符切成最多 fields.size() 段，依次按 fields 的字段名映射；
     * 段数不够的字段留空，最后一段吞掉剩余原文（SK 等内部的单个 '-' 不受影响）。
     * 空行忽略，首尾空白清除。fields 为空时走默认顺序。
     */
    public List<ClaudeAccount> parse(String rawData, List<String> fields) {
        List<ClaudeAccount> out = new ArrayList<>();
        if (rawData == null) {
            return out;
        }
        List<String> order = (fields == null || fields.isEmpty()) ? DEFAULT_FIELDS : fields;
        for (String line : rawData.split("\n")) {
            String raw = line.replaceAll("\r$", "").trim();
            if (raw.isEmpty()) {
                continue;
            }
            // limit=order.size()：最后一个字段保留剩余原文，字段内部的单个 '-' 不会被当分隔符
            String[] segs = raw.split("-{2,}", order.size());
            ClaudeAccount a = new ClaudeAccount();
            for (int i = 0; i < order.size() && i < segs.length; i++) {
                assign(a, order.get(i), segs[i].trim());
            }
            a.setRawData(raw);
            out.add(a);
        }
        return out;
    }

    /** 解析并保存，createdBy 为录入人（登录账号）。skipDuplicates=true 时跳过重复（库中已有或批内重复）。 */
    public List<ClaudeAccount> parseAndSave(String rawData, List<String> fields, boolean skipDuplicates, String createdBy) {
        List<ClaudeAccount> parsed = parse(rawData, fields);
        List<ClaudeAccount> toSave;
        if (skipDuplicates) {
            toSave = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ClaudeAccount a : parsed) {
                // 批内重复或库中已存在都跳过
                if (seen.add(signature(a)) && !existsInDb(a)) {
                    toSave.add(a);
                }
            }
        } else {
            toSave = parsed;
        }
        if (createdBy != null && !createdBy.isBlank()) {
            toSave.forEach(a -> a.setCreatedBy(createdBy));
        }
        return repository.saveAll(toSave);
    }

    /** 查重：逐行比对库中已有记录，五字段完全一致（含批内重复）即算重复。 */
    public ClaudeDupCheck check(String rawData, List<String> fields) {
        List<ClaudeAccount> parsed = parse(rawData, fields);
        List<String> duplicates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ClaudeAccount a : parsed) {
            boolean dupInBatch = !seen.add(signature(a));
            if (dupInBatch || existsInDb(a)) {
                duplicates.add(a.getRawData());
            }
        }
        ClaudeDupCheck r = new ClaudeDupCheck();
        r.setTotal(parsed.size());
        r.setDupCount(duplicates.size());
        r.setNewCount(parsed.size() - duplicates.size());
        r.setDuplicates(duplicates);
        return r;
    }

    private boolean existsInDb(ClaudeAccount a) {
        return repository.countMatching(a.getClaudeAccount(), a.getEmail(),
                a.getEmailPassword(), a.getSk(), a.getAccountId()) > 0;
    }

    /** 五字段签名，用于批内去重（null 与空串区分，用不可见分隔符避免误拼）。 */
    private static String signature(ClaudeAccount a) {
        return nz(a.getClaudeAccount()) + '\u0001' + nz(a.getEmail()) + '\u0001'
                + nz(a.getEmailPassword()) + '\u0001' + nz(a.getSk()) + '\u0001' + nz(a.getAccountId());
    }

    private static String nz(String s) {
        return s == null ? "\u0000" : s;
    }

    public Page<ClaudeAccount> list(String keyword, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);
        return repository.search(keyword, PageRequest.of(pageIndex, size));
    }

    public ClaudeAccount update(ClaudeAccount account) {
        return repository.save(account);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    /** 把某段写入对应字段；不认识的字段名直接忽略（该段丢弃）。 */
    private static void assign(ClaudeAccount a, String field, String value) {
        BiConsumer<ClaudeAccount, String> setter = switch (field) {
            case "claudeAccount" -> ClaudeAccount::setClaudeAccount;
            case "email" -> ClaudeAccount::setEmail;
            case "emailPassword" -> ClaudeAccount::setEmailPassword;
            case "sk" -> ClaudeAccount::setSk;
            case "accountId" -> ClaudeAccount::setAccountId;
            default -> null;
        };
        if (setter != null) {
            setter.accept(a, value);
        }
    }
}
