package com.sharp.dto;

import lombok.Data;

import java.util.List;

/**
 * Apple 三个子模块共用的录入请求。
 * appleAccountId 与 appleId 给其一：前者用已有 Apple ID，后者顺手建（或复用）一个。
 */
@Data
public class AppleRequest {

    /* —— Apple ID —— */

    /** 已有 Apple ID 的主键 */
    private Long appleAccountId;

    /** Apple ID 账号，用于新增场景 */
    private String appleId;

    /** 备注 */
    private String note;

    /* —— 子模块一：转发邮箱 —— */

    /** 转发邮箱地址 */
    private String forwardEmail;

    /** 录入后是否设为当前转发邮箱 */
    private Boolean setCurrent;

    /* —— 子模块二：隐藏邮箱 —— */

    /** 所属转发邮箱；不传则挂到该 Apple ID 的当前转发邮箱 */
    private Long forwardEmailId;

    /** 隐藏邮箱地址 */
    private String hideEmail;

    /** 批量录入的隐藏邮箱地址 */
    private List<String> hideEmails;

    /* —— 子模块三：黑客邮箱 —— */

    /** 隐藏邮箱主键，与 hideEmail 给其一 */
    private Long hideEmailId;

    /** 黑客邮箱地址 */
    private String hackerEmail;

    /** 批量绑定的明细：每项给 hideEmail + hackerEmail */
    private List<AppleRequest> items;

    /* —— 模块四 / 五：倒推接码 —— */

    /** 入口类型：hide（隐藏邮箱）/ hacker（黑客邮箱） */
    private String entryType;

    /** 取件条数上限 */
    private Integer limit;
}
