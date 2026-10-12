package com.sharp.service;

import com.sharp.dto.SupplierDupCheck;
import com.sharp.entity.Supplier;
import com.sharp.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

@Service
public class SupplierService {

    private final SupplierRepository repository;

    /** 默认字段顺序：IP / 端口 / 用户名 / 密码 / 地区 / 到期时间。 */
    private static final List<String> DEFAULT_FIELDS =
            List.of("ip", "port", "username", "password", "region", "expireTime");

    public SupplierService(SupplierRepository repository) {
        this.repository = repository;
    }

    /**
     * 解析多行原始串。每行按单个冒号切成最多 fields.size() 段，依次按 fields 的字段名映射；
     * 段数不够的字段留空，最后一段吞掉剩余原文（默认顺序下到期时间带 "HH:mm:ss" 不会被拆开）。
     * 空行忽略，首尾空白清除。fields 为空时走默认顺序。
     */
    public List<Supplier> parse(String rawData, List<String> fields) {
        List<Supplier> out = new ArrayList<>();
        if (rawData == null) {
            return out;
        }
        List<String> order = (fields == null || fields.isEmpty()) ? DEFAULT_FIELDS : fields;
        for (String line : rawData.split("\n")) {
            String raw = line.replaceAll("\r$", "").trim();
            if (raw.isEmpty()) {
                continue;
            }
            String[] segs = raw.split(":", order.size());
            Supplier s = new Supplier();
            for (int i = 0; i < order.size() && i < segs.length; i++) {
                assign(s, order.get(i), segs[i].trim());
            }
            s.setRawData(raw);
            out.add(s);
        }
        return out;
    }

    /** 解析并保存，createdBy 为录入人（登录账号）。skipDuplicates=true 时跳过重复（库中已有或批内重复）。 */
    public List<Supplier> parseAndSave(String rawData, List<String> fields, boolean skipDuplicates, String createdBy) {
        List<Supplier> parsed = parse(rawData, fields);
        List<Supplier> toSave;
        if (skipDuplicates) {
            toSave = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Supplier s : parsed) {
                // 批内重复或库中已存在都跳过
                if (seen.add(signature(s)) && !existsInDb(s)) {
                    toSave.add(s);
                }
            }
        } else {
            toSave = parsed;
        }
        if (createdBy != null && !createdBy.isBlank()) {
            toSave.forEach(s -> s.setCreatedBy(createdBy));
        }
        return repository.saveAll(toSave);
    }

    /** 查重：逐行比对库中已有记录，六字段完全一致（含批内重复）即算重复。 */
    public SupplierDupCheck check(String rawData, List<String> fields) {
        List<Supplier> parsed = parse(rawData, fields);
        List<String> duplicates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Supplier s : parsed) {
            boolean dupInBatch = !seen.add(signature(s));
            if (dupInBatch || existsInDb(s)) {
                duplicates.add(s.getRawData());
            }
        }
        SupplierDupCheck r = new SupplierDupCheck();
        r.setTotal(parsed.size());
        r.setDupCount(duplicates.size());
        r.setNewCount(parsed.size() - duplicates.size());
        r.setDuplicates(duplicates);
        return r;
    }

    private boolean existsInDb(Supplier s) {
        return repository.countMatching(s.getIp(), s.getPort(), s.getUsername(),
                s.getPassword(), s.getRegion(), s.getExpireTime()) > 0;
    }

    /** 六字段签名，用于批内去重（null 与空串区分，用不可见分隔符避免误拼）。 */
    private static String signature(Supplier s) {
        return nz(s.getIp()) + '\u0001' + nz(s.getPort()) + '\u0001' + nz(s.getUsername()) + '\u0001'
                + nz(s.getPassword()) + '\u0001' + nz(s.getRegion()) + '\u0001' + nz(s.getExpireTime());
    }

    private static String nz(String s) {
        return s == null ? "\u0000" : s;
    }

    public Page<Supplier> list(String keyword, int page, int size) {
        int pageIndex = Math.max(page - 1, 0);
        return repository.search(keyword, PageRequest.of(pageIndex, size));
    }

    public Supplier update(Supplier supplier) {
        return repository.save(supplier);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    /** 把某段写入对应字段；不认识的字段名直接忽略（该段丢弃）。 */
    private static void assign(Supplier s, String field, String value) {
        BiConsumer<Supplier, String> setter = switch (field) {
            case "ip" -> Supplier::setIp;
            case "port" -> Supplier::setPort;
            case "username" -> Supplier::setUsername;
            case "password" -> Supplier::setPassword;
            case "region" -> Supplier::setRegion;
            case "expireTime" -> Supplier::setExpireTime;
            default -> null;
        };
        if (setter != null) {
            setter.accept(s, value);
        }
    }
}
