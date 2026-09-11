package com.sharp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

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
}
