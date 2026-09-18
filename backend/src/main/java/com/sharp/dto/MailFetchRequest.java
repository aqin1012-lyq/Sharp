package com.sharp.dto;

import lombok.Data;

/**
 * 邮件取件请求。凭据来源二选一：
 * 1) 直接传 email + refreshToken + clientId；
 * 2) 传 accountId，从已入库的 Outlook 账号里取上述三字段。
 */
@Data
public class MailFetchRequest {

    /** 已入库账号 id（传了则优先按此取凭据） */
    private Long accountId;

    /** 邮箱地址 */
    private String email;

    /** refresh token（outlook） */
    private String refreshToken;

    /** client id（outlook） */
    private String clientId;

    /** 要读取的文件夹：inbox / junk / all，默认 all */
    private String folder;

    /** 拉取条数，默认 10，上限 30 */
    private Integer limit;
}
