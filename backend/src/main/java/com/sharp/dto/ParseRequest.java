package com.sharp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 解析 / 保存请求。rawData 可以是单行，也可以是多行（每行一条记录）。
 */
@Data
public class ParseRequest {

    /** 邮箱类型：gmail / 012e / outlook */
    @NotBlank(message = "emailType 不能为空")
    private String emailType;

    /** 原始信息（支持多行，一行一条） */
    @NotBlank(message = "rawData 不能为空")
    private String rawData;

    /**
     * 可选的字段顺序覆盖（前端拖拽调整后才会传）。
     * 元素为字段名，如 ["email", "password", "extraUrl"]，第 i 段写入第 i 个字段；
     * 不认识的字段名（如占位用的 "ignore"）会被跳过。为空时走各类型的默认解析规则。
     */
    private List<String> fields;
}
