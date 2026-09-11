package com.sharp;

import com.sharp.entity.EmailAccount;
import com.sharp.service.EmailParserService;
import org.junit.jupiter.api.Test;

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
}
