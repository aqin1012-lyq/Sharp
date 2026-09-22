package com.sharp.dto;

import lombok.Data;

/**
 * 取回的单封邮件：纯文本正文（验证码提取/预览/复制用）+ 原始 HTML（前端按原样渲染）。
 */
@Data
public class MailMessageDto {

    /** 发件人邮箱地址 */
    private String from;

    /** 发件人显示名 */
    private String fromName;

    /** 收件人（多个以 , 分隔） */
    private String to;

    /** 抄送（多个以 , 分隔） */
    private String cc;

    /** 回复地址 */
    private String replyTo;

    /**
     * 投递 / 转发链路：Delivered-To、X-Forwarded-To、X-Original-To、Envelope-To
     * 以及 Received 头里 {@code for <addr>} 的地址，去重后以 , 分隔。
     * 隐藏邮箱、别名转发的真实落点就在这里。
     */
    private String forwardedTo;

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

    /** 原始 HTML 正文（已轻量清洗，可空）；前端用沙箱 iframe 按原样渲染 */
    private String bodyHtml;

    /** 从主题/正文提取到的验证码，可空 */
    private String verifyCode;
}
