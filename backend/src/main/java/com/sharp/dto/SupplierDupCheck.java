package com.sharp.dto;

import lombok.Data;

import java.util.List;

/** 查重结果：解析后逐行比对库中已有记录（六字段完全一致，含批内重复）。 */
@Data
public class SupplierDupCheck {

    /** 解析出的总条数 */
    private int total;

    /** 重复条数（库中已存在或批内重复） */
    private int dupCount;

    /** 新数据条数（= total - dupCount） */
    private int newCount;

    /** 重复行的原始串，供弹框展示 */
    private List<String> duplicates;
}
