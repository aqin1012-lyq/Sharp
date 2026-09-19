package com.sharp.service;

import com.sharp.entity.EmailAccount;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 原始信息拆分器。
 *
 * 三种邮箱的原始信息结构：
 *
 * 1) gmail，两种布局按分隔符自动识别：
 *    a) 含 "|"（旧，共 8 段）：邮箱 | 密码 | 备用邮箱 | key | 年份 | 国家 | 辅助验证码 | 链接
 *       例：smi23edl21@gmail.com|bG8Rg22M|4ln...@fastmailapp.com|ysf...|2021|India|4O5G S1X2 ...|https://...
 *    b) 含 "----"（新，共 5 段）：邮箱 ---- 密码 ---- 备用邮箱 ---- 2FA备用码 ---- 2FA链接
 *       例：gfa...@gmail.com----m61...----Farah...@outlook.com----6rza nf53 ...----https://2fas.../#...
 *
 * 2) 012e（以 3 个及以上的 "-" 分隔，容忍 "---" 与 "----"，共 3 段）：
 *    邮箱 ---- 密码 --- 取件链接
 *    例：2w4vsfe430@012e.com----0m87---http://mail.012e.com/api/getcode.php?token=...
 *
 * 3) outlook（以 "----" 分隔）：
 *    邮箱 ---- 密码 ---- refreshToken ---- clientId ---- 说明 ---- "cookie" ---- cookie值
 *    例：stktcf85773@outlook.com----tntmbg78880----M.C541_...$$----9e5f...----请复制...----cookie----sk-ant-...
 *
 * 除上述默认规则外，调用方可以传入一份字段顺序（前端拖拽调整后的结果）来覆盖映射关系，
 * 此时只按分隔符切段并逐位写入指定字段，见 {@link #parseLine(String, String, List)}。
 */
@Service
public class EmailParserService {

    public static final String TYPE_GMAIL = "gmail";
    public static final String TYPE_012E = "012e";
    public static final String TYPE_OUTLOOK = "outlook";
    /** 混合批量：每行按内容自动识别类型 */
    public static final String TYPE_AUTO = "auto";
    /** 邮箱后缀认不出时的兜底类型 */
    public static final String TYPE_OTHER = "other";

    /** 字段名 -> 赋值方法。字段顺序覆盖时用它把第 i 段写进对应字段；表中没有的名字（如占位符 "ignore"）直接跳过。 */
    private static final Map<String, BiConsumer<EmailAccount, String>> SETTERS = Map.ofEntries(
            Map.entry("email", EmailAccount::setEmail),
            Map.entry("password", EmailAccount::setPassword),
            Map.entry("recoveryEmail", EmailAccount::setRecoveryEmail),
            Map.entry("recoveryKey", EmailAccount::setRecoveryKey),
            Map.entry("regYear", EmailAccount::setRegYear),
            Map.entry("country", EmailAccount::setCountry),
            Map.entry("authKey", EmailAccount::setAuthKey),
            Map.entry("extraUrl", EmailAccount::setExtraUrl),
            Map.entry("refreshToken", EmailAccount::setRefreshToken),
            Map.entry("clientId", EmailAccount::setClientId),
            Map.entry("note", EmailAccount::setNote),
            Map.entry("cookie", EmailAccount::setCookie),
            Map.entry("uuid", EmailAccount::setUuid),
            Map.entry("token", EmailAccount::setToken)
    );

    /** 支持多行：一行一条，返回解析后的账号列表（不落库）。 */
    public List<EmailAccount> parseMultiline(String emailType, String rawData) {
        return parseMultiline(emailType, rawData, null);
    }

    /** 支持多行，并可用 fields 覆盖字段顺序。 */
    public List<EmailAccount> parseMultiline(String emailType, String rawData, List<String> fields) {
        List<EmailAccount> list = new ArrayList<>();
        if (rawData == null) {
            return list;
        }
        for (String line : rawData.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            list.add(parseLine(emailType, trimmed, fields));
        }
        return list;
    }

    /** 解析单行（默认字段顺序）。 */
    public EmailAccount parseLine(String emailType, String raw) {
        return parseLine(emailType, raw, null);
    }

    /**
     * 解析单行。
     *
     * @param fields 字段顺序覆盖，为空时走各类型的默认规则；非空时按该顺序逐位映射切分出来的段。
     */
    public EmailAccount parseLine(String emailType, String raw, List<String> fields) {
        if (emailType == null) {
            throw new IllegalArgumentException("emailType 不能为空");
        }
        String type = emailType.trim().toLowerCase();
        if (TYPE_AUTO.equals(type)) {
            // 自动识别：传了字段顺序则按该顺序逐位映射（类型由邮箱后缀判定，不限三种）；
            // 未传则按本行内容识别为三种之一，走其默认结构。
            if (fields != null && !fields.isEmpty()) {
                return parseAutoByFields(raw, fields);
            }
            type = detectType(raw);
        } else if (fields != null && !fields.isEmpty()) {
            return parseByFields(type, raw, fields);
        }
        return switch (type) {
            case TYPE_GMAIL -> parseGmail(raw);
            case TYPE_012E -> parse012e(raw);
            case TYPE_OUTLOOK -> parseOutlook(raw);
            default -> throw new IllegalArgumentException("不支持的邮箱类型: " + emailType);
        };
    }

    /**
     * 按单行内容识别邮箱类型（用于 auto 混合批量）：
     * 先看主邮箱（第一段）域名，命中 gmail/012e/outlook 直接返回；
     * 否则按分隔符与段数兜底猜测。识别不出时默认 gmail。
     */
    public String detectType(String raw) {
        if (raw == null || raw.isBlank()) {
            return TYPE_GMAIL;
        }
        // 第一段（主邮箱）：先按 3+ 连字符或 "|" 切出首段
        String first = raw.split("-{3,}|\\|", 2)[0].trim().toLowerCase();
        if (first.endsWith("@gmail.com")) {
            return TYPE_GMAIL;
        }
        if (first.endsWith("@012e.com")) {
            return TYPE_012E;
        }
        if (first.endsWith("@outlook.com")) {
            return TYPE_OUTLOOK;
        }
        // 域名认不出，按结构兜底
        if (raw.contains("|")) {
            return TYPE_GMAIL;                        // "|" 布局只有 gmail 旧格式
        }
        if (raw.contains("----")) {
            int segs = raw.split("----").length;
            if (segs >= 6) {
                return TYPE_OUTLOOK;                  // outlook 段数最多（含 cookie 标记）
            }
            if (segs <= 3) {
                return TYPE_012E;                     // 012e 只有 3 段
            }
            return TYPE_GMAIL;                        // 4~5 段按 gmail 新格式
        }
        if (raw.split("-{3,}").length >= 2) {
            return TYPE_012E;                         // 仅 "---" 分隔，视作 012e
        }
        return TYPE_GMAIL;
    }

    /** 按调用方给定的字段顺序逐位映射：第 i 段 -> fields[i]，最后一个字段吞掉剩余所有段。 */
    private EmailAccount parseByFields(String type, String raw, List<String> fields) {
        if (!TYPE_GMAIL.equals(type) && !TYPE_012E.equals(type) && !TYPE_OUTLOOK.equals(type)) {
            throw new IllegalArgumentException("不支持的邮箱类型: " + type);
        }
        EmailAccount a = new EmailAccount();
        a.setEmailType(type);
        a.setRawData(raw);
        // limit = 字段数：切分最多进行 (n-1) 次，末段保留剩余原文，
        // 这样 token / cookie 内部即使含分隔符也不会被截断。
        String[] p = splitSegments(type, raw, fields.size());
        for (int i = 0; i < fields.size(); i++) {
            BiConsumer<EmailAccount, String> setter = SETTERS.get(fields.get(i));
            if (setter != null) {
                setter.accept(a, get(p, i));
            }
        }
        return a;
    }

    /** 各 VARCHAR 列的最大长度（与 schema.sql 一致）；未列出的字段为 TEXT，不限长。 */
    private static final Map<String, Integer> MAX_LEN = Map.of(
            "email", 255,
            "password", 255,
            "recoveryEmail", 255,
            "regYear", 16,
            "country", 64,
            "extraUrl", 1024,
            "clientId", 128,
            "note", 1024,
            "uuid", 128
    );

    /**
     * 自动识别 + 字段顺序：按每行分隔符（含 "|" 用 "|"，否则按 3+ 连字符）切段，逐位映射到 fields，
     * 类型由邮箱后缀判定（不限三种）。每个值按目标列最大长度截断，避免字段顺序与某行不匹配时
     * 长值撑爆短列导致整批入库失败（原始整行完整存于 rawData）。
     */
    private EmailAccount parseAutoByFields(String raw, List<String> fields) {
        EmailAccount a = new EmailAccount();
        a.setRawData(raw);
        String s = raw == null ? "" : raw;
        String[] p = s.contains("|") ? s.split("\\|", fields.size()) : s.split("-{3,}", fields.size());
        for (int i = 0; i < fields.size(); i++) {
            String field = fields.get(i);
            BiConsumer<EmailAccount, String> setter = SETTERS.get(field);
            if (setter != null) {
                setter.accept(a, clampToColumn(field, get(p, i)));
            }
        }
        a.setEmailType(classifyByEmail(a.getEmail()));
        return a;
    }

    /** 按目标列最大长度截断（TEXT 列不限长）。 */
    private String clampToColumn(String field, String value) {
        if (value == null) {
            return null;
        }
        Integer max = MAX_LEN.get(field);
        return (max != null && value.length() > max) ? value.substring(0, max) : value;
    }

    /**
     * 按邮箱后缀分类：三种已知域名归为 gmail / 012e / outlook；其余取 "@" 后的域名作为类型；
     * 无有效邮箱时归为 "other"。结果截断到 email_type 列长（32）。
     */
    private String classifyByEmail(String email) {
        String type = TYPE_OTHER;
        if (email != null) {
            int at = email.lastIndexOf('@');
            if (at >= 0 && at < email.length() - 1) {
                String domain = email.substring(at + 1).trim().toLowerCase();
                if (!domain.isEmpty()) {
                    type = switch (domain) {
                        case "gmail.com" -> TYPE_GMAIL;
                        case "012e.com" -> TYPE_012E;
                        case "outlook.com" -> TYPE_OUTLOOK;
                        default -> domain;
                    };
                }
            }
        }
        return type.length() > 32 ? type.substring(0, 32) : type;
    }

    /** 按邮箱类型的分隔符切段，切到底。 */
    private String[] splitSegments(String type, String raw) {
        return splitSegments(type, raw, 0);
    }

    /**
     * 按邮箱类型的分隔符切段。
     *
     * @param limit 同 {@link String#split(String, int)}：正数表示最多切 limit-1 次，末段保留剩余原文；0 为切到底。
     */
    private String[] splitSegments(String type, String raw, int limit) {
        String s = raw == null ? "" : raw;
        return switch (type) {
            // gmail 两种布局共存：含 "|" 走旧格式，否则按 "----"
            case TYPE_GMAIL -> s.contains("|") ? s.split("\\|", limit) : s.split("----", limit);
            // 012e 容忍 "---" 与 "----"
            case TYPE_012E -> s.split("-{3,}", limit);
            default -> s.split("----", limit);
        };
    }

    private EmailAccount parseGmail(String raw) {
        EmailAccount a = new EmailAccount();
        a.setEmailType(TYPE_GMAIL);
        a.setRawData(raw);
        String[] p = splitSegments(TYPE_GMAIL, raw);
        if (raw != null && raw.contains("|")) {
            // 旧格式（8 段，"|" 分隔）：邮箱|密码|备用邮箱|key|年份|国家|辅助验证码|链接
            a.setEmail(get(p, 0));
            a.setPassword(get(p, 1));
            a.setRecoveryEmail(get(p, 2));
            a.setRecoveryKey(get(p, 3));
            a.setRegYear(get(p, 4));
            a.setCountry(get(p, 5));
            a.setAuthKey(get(p, 6));
            a.setExtraUrl(get(p, 7));
        } else {
            // 新格式（5 段，"----" 分隔）：邮箱----密码----备用邮箱----2FA备用码----2FA链接
            a.setEmail(get(p, 0));
            a.setPassword(get(p, 1));
            a.setRecoveryEmail(get(p, 2));
            a.setAuthKey(get(p, 3));
            a.setExtraUrl(get(p, 4));
        }
        return a;
    }

    private EmailAccount parse012e(String raw) {
        EmailAccount a = new EmailAccount();
        a.setEmailType(TYPE_012E);
        a.setRawData(raw);
        // 容忍 "---" 和 "----"：按 3 个及以上的连字符切分
        String[] p = splitSegments(TYPE_012E, raw);
        a.setEmail(get(p, 0));
        a.setPassword(get(p, 1));
        a.setExtraUrl(get(p, 2));
        return a;
    }

    private EmailAccount parseOutlook(String raw) {
        EmailAccount a = new EmailAccount();
        a.setEmailType(TYPE_OUTLOOK);
        a.setRawData(raw);
        String[] p = splitSegments(TYPE_OUTLOOK, raw);
        a.setEmail(get(p, 0));
        a.setPassword(get(p, 1));
        a.setRefreshToken(get(p, 2));
        a.setClientId(get(p, 3));

        // 第 5 段起可能是：说明、"cookie" 标签、cookie 值，顺序或存在与否不完全固定，做容错处理。
        StringBuilder noteBuilder = new StringBuilder();
        String cookieValue = null;
        for (int i = 4; i < p.length; i++) {
            String seg = p[i] == null ? "" : p[i].trim();
            if (seg.isEmpty()) {
                continue;
            }
            if (seg.equalsIgnoreCase("cookie")) {
                // 下一段即为 cookie 值
                if (i + 1 < p.length) {
                    cookieValue = p[i + 1] == null ? "" : p[i + 1].trim();
                    i++;
                }
            } else {
                if (noteBuilder.length() > 0) {
                    noteBuilder.append(" ");
                }
                noteBuilder.append(seg);
            }
        }
        a.setNote(noteBuilder.length() > 0 ? noteBuilder.toString() : null);
        a.setCookie(cookieValue);
        return a;
    }

    private String get(String[] arr, int idx) {
        if (arr == null || idx >= arr.length) {
            return null;
        }
        String v = arr[idx];
        if (v == null) {
            return null;
        }
        v = v.trim();
        return v.isEmpty() ? null : v;
    }
}
