package com.sharp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 供应商解析 / 保存请求。rawData 支持多行，一行一条，每行按单个冒号拆段。
 */
@Data
public class SupplierParseRequest {

    /** 原始信息（支持多行，一行一条） */
    @NotBlank(message = "rawData 不能为空")
    private String rawData;

    /**
     * 可选的字段顺序（前端拖拽调整后传）。元素为字段名，如
     * ["ip", "port", "username", "password", "region", "expireTime"]，第 i 段写入第 i 个字段。
     * 不认识的字段名会被跳过（该段丢弃）；为空时走默认顺序。
     */
    private List<String> fields;

    /** 保存时是否跳过重复（六字段与库中已有记录完全一致，含批内重复）。默认 false（全部入库）。 */
    private boolean skipDuplicates;
}
