package com.sharp;

import com.sharp.entity.EmailAccount;
import com.sharp.service.EmailParserService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmailParserServiceTest {

    private final EmailParserService parser = new EmailParserService();

    @Test
    void testGmail() {
        String raw = "smi23edl21@gmail.com|bG8Rg22M|4ln532j332488c3@fastmailapp.com|ysfsdcdf3lqgmmq5w6y5gvsdphh7pfedvrr|2021|India|4O5G S1X2 2NCX Z6CB AQYK TMBB P7UY 4R32|https://2af.aigchl888.online/#NG8zZ3NxeG8ybnB4ejZjYmFxeWt0bXJicDd1TRyj";
        EmailAccount a = parser.parseLine("gmail", raw);
        assertEquals("gmail", a.getEmailType());
        assertEquals("smi23edl21@gmail.com", a.getEmail());
        assertEquals("bG8Rg22M", a.getPassword());
        assertEquals("4ln532j332488c3@fastmailapp.com", a.getRecoveryEmail());
        assertEquals("ysfsdcdf3lqgmmq5w6y5gvsdphh7pfedvrr", a.getRecoveryKey());
        assertEquals("2021", a.getRegYear());
        assertEquals("India", a.getCountry());
        assertEquals("4O5G S1X2 2NCX Z6CB AQYK TMBB P7UY 4R32", a.getAuthKey());
        assertEquals("https://2af.aigchl888.online/#NG8zZ3NxeG8ybnB4ejZjYmFxeWt0bXJicDd1TRyj", a.getExtraUrl());
    }

    @Test
    void testGmailDashFormat() {
        // 新格式（5 段，"----" 分隔）：邮箱----密码----备用邮箱----2FA备用码----2FA链接
        String raw = "gfaxvrunnllk5468@gmail.com----m61obw4oAwqfC----FarahSterrett55@outlook.com----6rza nf53 za5f bei2 lumi xnjn ek5k bwbf----https://2fas.nexoraivision.com/#NnJ6YW5mNTN6YTVmYmVpMmx1bWl4bmpuZWs1a2J3YmY";
        EmailAccount a = parser.parseLine("gmail", raw);
        assertEquals("gmail", a.getEmailType());
        assertEquals("gfaxvrunnllk5468@gmail.com", a.getEmail());
        assertEquals("m61obw4oAwqfC", a.getPassword());
        assertEquals("FarahSterrett55@outlook.com", a.getRecoveryEmail());
        assertEquals("6rza nf53 za5f bei2 lumi xnjn ek5k bwbf", a.getAuthKey());
        assertEquals("https://2fas.nexoraivision.com/#NnJ6YW5mNTN6YTVmYmVpMmx1bWl4bmpuZWs1a2J3YmY", a.getExtraUrl());
    }

    @Test
    void test012e() {
        String raw = "2w4vsfe430@012e.com----0m87---http://mail.012e.com/api/getcode.php?token=Mnc0djNzMzBAdsdsaEyZS5w20tLS0tMG05Mdsfdf";
        EmailAccount a = parser.parseLine("012e", raw);
        assertEquals("012e", a.getEmailType());
        assertEquals("2w4vsfe430@012e.com", a.getEmail());
        assertEquals("0m87", a.getPassword());
        assertEquals("http://mail.012e.com/api/getcode.php?token=Mnc0djNzMzBAdsdsaEyZS5w20tLS0tMG05Mdsfdf", a.getExtraUrl());
    }

    @Test
    void testOutlook() {
        String raw = "stktcf85773@outlook.com----tntmbg78880----M.C541_BAY.0.U.MsaArtihbjhts$$----9e5f9s6xc-e8a7-4e66-b84t-63364c29dsadsa4----请复制前面所有数据（最后----去掉）到https://2fa.run/mail粘贴选HTML显示或到https://outlook.office.com官方登录----cookie----sk-ant-sid02-abc123";
        EmailAccount a = parser.parseLine("outlook", raw);
        assertEquals("outlook", a.getEmailType());
        assertEquals("stktcf85773@outlook.com", a.getEmail());
        assertEquals("tntmbg78880", a.getPassword());
        assertEquals("M.C541_BAY.0.U.MsaArtihbjhts$$", a.getRefreshToken());
        assertEquals("9e5f9s6xc-e8a7-4e66-b84t-63364c29dsadsa4", a.getClientId());
        assertNotNull(a.getNote());
        assertTrue(a.getNote().contains("请复制"));
        assertEquals("sk-ant-sid02-abc123", a.getCookie());
    }

    @Test
    void testMultiline() {
        String raw = "2w4vsfe430@012e.com----0m87---http://a.com\n1x2y3z@012e.com----pass2---http://b.com";
        var list = parser.parseMultiline("012e", raw);
        assertEquals(2, list.size());
        assertEquals("1x2y3z@012e.com", list.get(1).getEmail());
    }

    @Test
    void testCustomFieldOrder() {
        // 拖拽后：邮箱 ---- 取件链接 ---- 密码
        String raw = "2w4vsfe430@012e.com----http://a.com----0m87";
        EmailAccount a = parser.parseLine("012e", raw, List.of("email", "extraUrl", "password"));
        assertEquals("012e", a.getEmailType());
        assertEquals("2w4vsfe430@012e.com", a.getEmail());
        assertEquals("http://a.com", a.getExtraUrl());
        assertEquals("0m87", a.getPassword());
        assertEquals(raw, a.getRawData());
    }

    @Test
    void testCustomFieldOrderSkipsPlaceholderAndMissingSegments() {
        // "ignore" 为占位字段（对应原始串里固定的 "cookie" 标记），不写入任何列；
        // fields 比实际段数长时，多出来的字段留空。
        String raw = "a@outlook.com----pwd----cookie----sk-ant-123";
        EmailAccount a = parser.parseLine("outlook", raw,
                List.of("email", "password", "ignore", "cookie", "clientId"));
        assertEquals("a@outlook.com", a.getEmail());
        assertEquals("pwd", a.getPassword());
        assertEquals("sk-ant-123", a.getCookie());
        assertNull(a.getNote());
        assertNull(a.getClientId());
    }

    @Test
    void testCustomFieldOrderGmailPipeLayout() {
        // gmail 含 "|" 时仍按 "|" 切段，字段顺序照给定的来
        String raw = "a@gmail.com|2021|pwd";
        EmailAccount a = parser.parseLine("gmail", raw, List.of("email", "regYear", "password"));
        assertEquals("a@gmail.com", a.getEmail());
        assertEquals("2021", a.getRegYear());
        assertEquals("pwd", a.getPassword());
    }

    @Test
    void testEmptyFieldsFallsBackToDefaultRules() {
        String raw = "2w4vsfe430@012e.com----0m87---http://a.com";
        EmailAccount a = parser.parseLine("012e", raw, List.of());
        assertEquals("0m87", a.getPassword());
        assertEquals("http://a.com", a.getExtraUrl());
    }

    @Test
    void testUuidAndToken() {
        String raw = "a@012e.com----pwd----http://a.com----550e8400-e29b-41d4-a716-446655440000----tk_abc123";
        EmailAccount a = parser.parseLine("012e", raw,
                List.of("email", "password", "extraUrl", "uuid", "token"));
        assertEquals("550e8400-e29b-41d4-a716-446655440000", a.getUuid());
        assertEquals("tk_abc123", a.getToken());
    }

    @Test
    void testUuidAndTokenLeftEmptyWhenAbsent() {
        // 模板里有 uuid / token，但原始串没那么多段 —— 留空，不影响前面的字段
        String raw = "a@012e.com----pwd----http://a.com";
        EmailAccount a = parser.parseLine("012e", raw,
                List.of("email", "password", "extraUrl", "uuid", "token"));
        assertEquals("a@012e.com", a.getEmail());
        assertEquals("http://a.com", a.getExtraUrl());
        assertNull(a.getUuid());
        assertNull(a.getToken());
    }

    @Test
    void testLastFieldKeepsSeparatorsInside() {
        // token 内部含 "----"：末段保留剩余原文，不被截断
        String raw = "a@outlook.com----pwd----M.C525_SN1.0.U.Msa----Artifacts----tail";
        EmailAccount a = parser.parseLine("outlook", raw, List.of("email", "password", "token"));
        assertEquals("a@outlook.com", a.getEmail());
        assertEquals("pwd", a.getPassword());
        assertEquals("M.C525_SN1.0.U.Msa----Artifacts----tail", a.getToken());
    }

    @Test
    void test012eLastFieldKeepsOriginalSeparator() {
        // 012e 的 "---" / "----" 混用，末段按原文保留而不是被规范化
        String raw = "a@012e.com----pwd----http://a.com?t=1---2";
        EmailAccount a = parser.parseLine("012e", raw, List.of("email", "password", "extraUrl"));
        assertEquals("http://a.com?t=1---2", a.getExtraUrl());
    }

    @Test
    void testDetectTypeByDomain() {
        assertEquals("gmail", parser.detectType("x@gmail.com|pw"));
        assertEquals("012e", parser.detectType("x@012e.com----pw----http://a.com"));
        assertEquals("outlook", parser.detectType("x@outlook.com----pw----rt----cid----note----cookie----v"));
    }

    @Test
    void testDetectTypeByStructureFallback() {
        // 域名不是三者之一时按结构兜底
        assertEquals("gmail", parser.detectType("x@foo.org|pw|rec|k|2021|US|auth|url")); // 有 "|"
        assertEquals("012e", parser.detectType("x@foo.org----pw----http://a.com"));       // 3 段
        assertEquals("outlook",
                parser.detectType("x@foo.org----pw----rt----cid----note----cookie----v")); // 7 段
    }

    @Test
    void testAutoMixedBatch() {
        // 一次粘贴三种不同类型，各按识别到的类型默认解析
        String raw = String.join("\n",
                "sg@gmail.com|pw1|rec@x.com|k|2016|US|4O5G S1X2|https://g.co",
                "2w4v@012e.com----0m87----http://mail.012e.com/getcode",
                "stk@outlook.com----tnt----M.C541----9e5f----请复制----cookie----sk-ant-1");
        var list = parser.parseMultiline("auto", raw, null);
        assertEquals(3, list.size());
        assertEquals("gmail", list.get(0).getEmailType());
        assertEquals("2016", list.get(0).getRegYear());
        assertEquals("012e", list.get(1).getEmailType());
        assertEquals("http://mail.012e.com/getcode", list.get(1).getExtraUrl());
        assertEquals("outlook", list.get(2).getEmailType());
        assertEquals("sk-ant-1", list.get(2).getCookie());
    }
}
