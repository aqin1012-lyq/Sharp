package com.sharp.service;

import com.sharp.entity.EmailAccount;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 原始信息拆分器。
 *
 * 三种邮箱的原始信息结构：
 *
 * 1) gmail（以 "|" 分隔，共 8 段）：
 *    邮箱 | 密码 | 备用邮箱 | key | 年份 | 国家 | 辅助验证码 | 链接
 *    例：smi23edl21@gmail.com|bG8Rg22M|4ln...@fastmailapp.com|ysf...|2021|India|4O5G S1X2 ...|https://...
 *
 * 2) 012e（以 3 个及以上的 "-" 分隔，容忍 "---" 与 "----"，共 3 段）：
 *    邮箱 ---- 密码 --- 取件链接
 *    例：2w4vsfe430@012e.com----0m87---http://mail.012e.com/api/getcode.php?token=...
 *
 * 3) outlook（以 "----" 分隔）：
 *    邮箱 ---- 密码 ---- refreshToken ---- clientId ---- 说明 ---- "cookie" ---- cookie值
 *    例：stktcf85773@outlook.com----tntmbg78880----M.C541_...$$----9e5f...----请复制...----cookie----sk-ant-...
 */
@Service
public class EmailParserService {

    public static final String TYPE_GMAIL = "gmail";
    public static final String TYPE_012E = "012e";
    public static final String TYPE_OUTLOOK = "outlook";

    /** 支持多行：一行一条，返回解析后的账号列表（不落库）。 */
    public List<EmailAccount> parseMultiline(String emailType, String rawData) {
        List<EmailAccount> list = new ArrayList<>();
        if (rawData == null) {
            return list;
        }
        for (String line : rawData.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            list.add(parseLine(emailType, trimmed));
        }
        return list;
    }

    /** 解析单行。 */
    public EmailAccount parseLine(String emailType, String raw) {
        if (emailType == null) {
            throw new IllegalArgumentException("emailType 不能为空");
        }
        String type = emailType.trim().toLowerCase();
        return switch (type) {
            case TYPE_GMAIL -> parseGmail(raw);
            case TYPE_012E -> parse012e(raw);
            case TYPE_OUTLOOK -> parseOutlook(raw);
            default -> throw new IllegalArgumentException("不支持的邮箱类型: " + emailType);
        };
    }

    private EmailAccount parseGmail(String raw) {
        EmailAccount a = new EmailAccount();
        a.setEmailType(TYPE_GMAIL);
        a.setRawData(raw);
        String[] p = raw.split("\\|");
        a.setEmail(get(p, 0));
        a.setPassword(get(p, 1));
        a.setRecoveryEmail(get(p, 2));
        a.setRecoveryKey(get(p, 3));
        a.setRegYear(get(p, 4));
        a.setCountry(get(p, 5));
        a.setAuthKey(get(p, 6));
        a.setExtraUrl(get(p, 7));
        return a;
    }

    private EmailAccount parse012e(String raw) {
        EmailAccount a = new EmailAccount();
        a.setEmailType(TYPE_012E);
        a.setRawData(raw);
        // 容忍 "---" 和 "----"：按 3 个及以上的连字符切分
        String[] p = raw.split("-{3,}");
        a.setEmail(get(p, 0));
        a.setPassword(get(p, 1));
        a.setExtraUrl(get(p, 2));
        return a;
    }

    private EmailAccount parseOutlook(String raw) {
        EmailAccount a = new EmailAccount();
        a.setEmailType(TYPE_OUTLOOK);
        a.setRawData(raw);
        String[] p = raw.split("----");
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
