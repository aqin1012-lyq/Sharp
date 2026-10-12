package com.sharp.service;

import com.sharp.entity.ClaudeAccount;
import com.sharp.repository.ClaudeAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ClaudeAccountService {

    private final ClaudeAccountRepository repository;

    public ClaudeAccountService(ClaudeAccountRepository repository) {
        this.repository = repository;
    }

    /**
     * 解析多行原始串。每行按 2+ 连字符切成最多 5 段，依次映射
     * claude账号 / 邮箱账号 / 邮箱密码 / SK / ID；段数不够的字段留空，
     * 第 5 段（ID）吞掉剩余原文。空行忽略，首尾空白清除。
     */
    public List<ClaudeAccount> parse(String rawData) {
        List<ClaudeAccount> out = new ArrayList<>();
        if (rawData == null) {
            return out;
        }
        for (String line : rawData.split("\n")) {
            String raw = line.replaceAll("\r$", "").trim();
            if (raw.isEmpty()) {
                continue;
            }
            // limit=5：SK 内部的单个 '-' 不会被当分隔符，ID 段保留剩余原文
            String[] segs = raw.split("-{2,}", 5);
            ClaudeAccount a = new ClaudeAccount();
            a.setClaudeAccount(seg(segs, 0));
            a.setEmail(seg(segs, 1));
            a.setEmailPassword(seg(segs, 2));
            a.setSk(seg(segs, 3));
            a.setAccountId(seg(segs, 4));
            a.setRawData(raw);
            out.add(a);
        }
        return out;
    }

    /** 解析并保存，createdBy 为录入人（登录账号）。 */
    public List<ClaudeAccount> parseAndSave(String rawData, String createdBy) {
        List<ClaudeAccount> parsed = parse(rawData);
        if (createdBy != null && !createdBy.isBlank()) {
            parsed.forEach(a -> a.setCreatedBy(createdBy));
        }
        return repository.saveAll(parsed);
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

    private static String seg(String[] segs, int i) {
        return i < segs.length ? segs[i].trim() : null;
    }
}
