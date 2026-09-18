package com.sharp.dto;

import lombok.Data;

/**
 * 取回的单封邮件（已转为纯文本），供前端展示与验证码提取。
 */
@Data
public class MailMessageDto {

    /** 发件人邮箱地址 */
    private String from;

    /** 发件人显示名 */
    private String fromName;

    /** 主题 */
    private String subject;

    /** 接收时间（ISO-8601 字符串） */
    private String date;

    /** 所在文件夹：收件箱 / 垃圾箱 */
    private String folder;

    /** 正文前 200 字预览（纯文本） */
    private String preview;

    /** 正文纯文本（HTML 已剥标签） */
    private String body;

    /** 从主题/正文提取到的验证码，可空 */
    private String verifyCode;
}
