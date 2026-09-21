package com.sharp;

import com.sharp.service.MailTextUtil;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** 覆盖嵌入前端的 HTML 正文：MIME 里怎么取、回传前怎么清洗。 */
class MailTextUtilTest {

    @Test
    void extractHtmlPrefersHtmlPartOfAlternative() throws Exception {
        MimeBodyPart plain = new MimeBodyPart();
        plain.setText("验证码 123456", "utf-8");
        MimeBodyPart html = new MimeBodyPart();
        html.setContent("<p>验证码 <b>123456</b></p>", "text/html; charset=utf-8");

        MimeMultipart alt = new MimeMultipart("alternative");
        alt.addBodyPart(plain);
        alt.addBodyPart(html);

        MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
        msg.setContent(alt);
        msg.saveChanges();

        assertEquals("<p>验证码 <b>123456</b></p>", MailTextUtil.extractHtml(msg));
        // 纯文本版本仍按原逻辑优先取 text/plain
        assertEquals("验证码 123456", MailTextUtil.extractText(msg).trim());
    }

    @Test
    void extractHtmlReturnsNullWhenOnlyPlainText() throws Exception {
        MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()));
        msg.setText("验证码 123456", "utf-8");
        msg.saveChanges();

        assertNull(MailTextUtil.extractHtml(msg));
    }

    @Test
    void sanitizeHtmlStripsScriptsButKeepsLayout() {
        String cleaned = MailTextUtil.sanitizeHtml(
                "<style>.c{color:red}</style>"
                        + "<script>alert(1)</script>"
                        + "<div style=\"padding:8px\" onclick=\"alert(2)\">"
                        + "<img src=\"https://x/a.png\" onerror='alert(3)'>"
                        + "<a href=\"javascript:alert(4)\">点我</a></div>"
                        + "<iframe src=\"https://evil\"></iframe>");

        assertFalse(cleaned.contains("alert(1)"));       // script 连内容一起去掉
        assertFalse(cleaned.contains("onclick"));
        assertFalse(cleaned.contains("onerror"));
        assertFalse(cleaned.toLowerCase().contains("javascript:"));
        assertFalse(cleaned.toLowerCase().contains("<iframe"));
        // 排版相关的一律保留
        assertTrue(cleaned.contains("<style>.c{color:red}</style>"));
        assertTrue(cleaned.contains("style=\"padding:8px\""));
        assertTrue(cleaned.contains("src=\"https://x/a.png\""));
    }

    @Test
    void sanitizeHtmlHandlesNullAndBlank() {
        assertNull(MailTextUtil.sanitizeHtml(null));
        assertNull(MailTextUtil.sanitizeHtml("<script>alert(1)</script>"));
    }
}
