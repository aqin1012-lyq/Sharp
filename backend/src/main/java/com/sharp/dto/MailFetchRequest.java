package com.sharp.dto;

import lombok.Data;

/**
 * 邮件取件请求。凭据来源二选一：
 * 1) 直接传字段（见下）；
 * 2) 传 accountId，从已入库账号里取对应字段。
 *
 * provider 决定走哪套取件逻辑：
 * - outlook：refreshToken + clientId + email（Graph 读信）
 * - gmail + authMode=oauth：refreshToken + clientId + clientSecret + email（IMAP XOAUTH2）
 * - gmail + authMode=password：email + password（应用专用密码，IMAP LOGIN）
 */
@Data
public class MailFetchRequest {

    /** 已入库账号 id（传了则优先按此取凭据，并按其 emailType 决定 provider） */
    private Long accountId;

    /** 邮箱服务商：outlook / gmail，默认 outlook（向后兼容） */
    private String provider;

    /** gmail 认证方式：oauth / password，默认 oauth；outlook 忽略 */
    private String authMode;

    /** 邮箱地址 */
    private String email;

    /** refresh token（outlook / gmail-oauth） */
    private String refreshToken;

    /** client id（outlook / gmail-oauth） */
    private String clientId;

    /** client secret（gmail-oauth，Google 换 token 必需） */
    private String clientSecret;

    /** 应用专用密码（gmail-password） */
    private String password;

    /** 要读取的文件夹：inbox / junk / all，默认 all */
    private String folder;

    /** 拉取条数，默认 10，上限 30 */
    private Integer limit;
}
