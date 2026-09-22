package com.sharp.service;

import jakarta.mail.Address;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 邮件正文/验证码提取的公共工具，供 Outlook(Graph) 与 Gmail(IMAP) 两个取件服务复用。
 * 纯静态、无状态。
 */
public final class MailTextUtil {

    private MailTextUtil() {
    }

    /** 提取验证码：优先取关键词附近的 4-8 位数字，兜底取正文第一个独立 4-8 位数字。 */
    private static final Pattern CODE_NEAR_KEYWORD = Pattern.compile(
            "(?:code|verification|verify|passcode|otp|one[-\\s]?time|安全代码|验证码|校验码|动态密码)"
                    + "[^0-9]{0,20}?(\\d{4,8})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_FALLBACK = Pattern.compile("(?<!\\d)(\\d{4,8})(?!\\d)");

    /** 嵌入前端的 HTML 正文最大保留长度（字符）。 */
    private static final int MAX_HTML_LEN = 512 * 1024;

    /** 从主题+正文提取验证码。 */
    public static String extractCode(String subject, String body) {
        String text = ((subject == null ? "" : subject) + "\n" + (body == null ? "" : body));
        Matcher m = CODE_NEAR_KEYWORD.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        // 主题里若单独出现验证码数字，优先于正文
        if (subject != null) {
            Matcher sm = CODE_FALLBACK.matcher(subject);
            if (sm.find()) {
                return sm.group(1);
            }
        }
        Matcher fm = CODE_FALLBACK.matcher(text);
        if (fm.find()) {
            return fm.group(1);
        }
        return null;
    }

    /** 极简 HTML → 纯文本：去脚本样式、块级标签换行、剥标签、反转义常见实体。 */
    public static String htmlToText(String html) {
        if (html == null) {
            return "";
        }
        String s = html
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|tr|li|h[1-6]|table)>", "\n")
                .replaceAll("<[^>]+>", " ");
        s = s.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
        // 折叠多余空白但保留换行结构
        s = s.replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    /** 递归取 IMAP/MIME 正文纯文本：优先 text/plain，其次 text/html（剥标签）。 */
    public static String extractText(Part part) throws Exception {
        if (part.isMimeType("text/plain")) {
            Object c = part.getContent();
            return c == null ? "" : c.toString();
        }
        if (part.isMimeType("text/html")) {
            Object c = part.getContent();
            return c == null ? "" : htmlToText(c.toString());
        }
        if (part.isMimeType("multipart/alternative")) {
            // alternative：优先纯文本版本
            Multipart mp = (Multipart) part.getContent();
            String html = null;
            for (int i = 0; i < mp.getCount(); i++) {
                Part bp = mp.getBodyPart(i);
                if (bp.isMimeType("text/plain")) {
                    Object c = bp.getContent();
                    if (c != null && !c.toString().isBlank()) {
                        return c.toString();
                    }
                } else if (bp.isMimeType("text/html")) {
                    Object c = bp.getContent();
                    if (c != null) {
                        html = htmlToText(c.toString());
                    }
                } else if (bp.isMimeType("multipart/*")) {
                    String nested = extractText(bp);
                    if (!nested.isBlank()) {
                        return nested;
                    }
                }
            }
            return html != null ? html : "";
        }
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                String s = extractText(mp.getBodyPart(i));
                if (!s.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(s);
                }
            }
            return sb.toString();
        }
        Object c = part.getContent();
        return c instanceof String s ? s : "";
    }

    /** 递归取 MIME 里的 HTML 正文原文；没有 HTML 版本返回 null。 */
    public static String extractHtml(Part part) throws Exception {
        if (part.isMimeType("text/html")) {
            Object c = part.getContent();
            return c == null ? null : c.toString();
        }
        if (part.isMimeType("multipart/alternative")) {
            // alternative 里越靠后的版本越「富」，取最后一个 HTML
            Multipart mp = (Multipart) part.getContent();
            String last = null;
            for (int i = 0; i < mp.getCount(); i++) {
                String s = extractHtml(mp.getBodyPart(i));
                if (s != null && !s.isBlank()) {
                    last = s;
                }
            }
            return last;
        }
        if (part.isMimeType("multipart/*")) {
            // mixed / related：正文通常是第一段，附件里的 html 不要
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                String s = extractHtml(mp.getBodyPart(i));
                if (s != null && !s.isBlank()) {
                    return s;
                }
            }
        }
        return null;
    }

    /**
     * 轻量清洗要嵌入前端的 HTML：去掉脚本、内联事件、可执行嵌入与自带 base，
     * 保留 style 与排版标签。真正的隔离靠前端 sandbox iframe（不给 allow-scripts），
     * 这里只是第二道闸。超长正文截断，避免个别营销邮件把响应撑爆。
     */
    public static String sanitizeHtml(String html) {
        if (html == null) {
            return null;
        }
        String s = html
                .replaceAll("(?is)<script[^>]*>.*?</script>", "")
                .replaceAll("(?is)<script[^>]*>", "")
                .replaceAll("(?is)</?(iframe|object|embed|applet|form|base)[^>]*>", "")
                .replaceAll("(?is)\\son[a-z]+\\s*=\\s*\"[^\"]*\"", "")
                .replaceAll("(?is)\\son[a-z]+\\s*=\\s*'[^']*'", "")
                .replaceAll("(?is)\\son[a-z]+\\s*=\\s*[^\\s>]+", "")
                .replaceAll("(?i)javascript:", "");
        if (s.length() > MAX_HTML_LEN) {
            s = s.substring(0, MAX_HTML_LEN);
        }
        return s.isBlank() ? null : s;
    }

    /** 投递 / 转发相关的邮件头，值直接就是地址。 */
    private static final List<String> FORWARD_HEADERS =
            List.of("Delivered-To", "X-Forwarded-To", "X-Original-To", "Envelope-To", "X-Delivered-To");
    /** Received 头里的 {@code for <addr>} 片段。 */
    private static final Pattern RECEIVED_FOR = Pattern.compile("(?i)\\bfor\\s+<?([^\\s<>;]+@[^\\s<>;]+)>?");

    /**
     * 从邮件头里提取投递 / 转发链路上的地址：先取 Delivered-To 一类头，
     * 再从 Received 头里捞 {@code for <addr>}。去重且保持出现顺序，没有则返回 null。
     * headers 的 key 需不区分大小写（两个取件服务都按此构造）。
     */
    public static String extractForwardedTo(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String name : FORWARD_HEADERS) {
            for (String v : headers.getOrDefault(name, List.of())) {
                addAddress(out, v);
            }
        }
        for (String received : headers.getOrDefault("Received", List.of())) {
            Matcher m = RECEIVED_FOR.matcher(received == null ? "" : received);
            while (m.find()) {
                addAddress(out, m.group(1));
            }
        }
        return out.isEmpty() ? null : String.join(", ", out);
    }

    /** 清掉尖括号/首尾空白后按小写入集合，空值忽略。 */
    private static void addAddress(LinkedHashSet<String> out, String raw) {
        if (raw == null) {
            return;
        }
        String s = raw.trim().replaceAll("^<|>$", "").trim().toLowerCase();
        if (!s.isEmpty() && s.contains("@")) {
            out.add(s);
        }
    }

    /** 地址数组 → "a@x.com, b@y.com"；空则返回 null。显示名丢弃，只留地址。 */
    public static String joinAddresses(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Address a : addresses) {
            if (a instanceof InternetAddress ia && ia.getAddress() != null) {
                addAddress(out, ia.getAddress());
            } else if (a != null) {
                addAddress(out, a.toString());
            }
        }
        return out.isEmpty() ? null : String.join(", ", out);
    }

    /** 解 MIME 编码字（如主题、发件人显示名）；失败原样返回。 */
    public static String decodeMime(String s) {
        if (s == null) {
            return null;
        }
        try {
            return MimeUtility.decodeText(s);
        } catch (Exception e) {
            return s;
        }
    }

    /** 截断到 max 字符，超出加省略号。 */
    public static String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
