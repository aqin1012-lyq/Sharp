package com.sharp.dto;

import lombok.Data;

/**
 * 录入一条隐藏邮箱的请求。
 * appleAccountId 与 appleId 给其一：前者用已有 Apple ID，后者顺手建（或复用）一个。
 */
@Data
public class AppleHideEmailRequest {

    /** 已有 Apple ID 的主键 */
    private Long appleAccountId;

    /** Apple ID 账号，用于新增场景 */
    private String appleId;

    /** Apple ID 备注，仅新增时有意义 */
    private String note;

    /** 隐藏邮箱 */
    private String hideEmail;

    /** HackerOne 邮箱（字段名沿用建表时的 redirect_email） */
    private String redirectEmail;

    /** 谷歌别名邮箱 */
    private String googleAliasEmail;
}
