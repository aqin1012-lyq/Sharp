package com.sharp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Claude 账号解析 / 保存请求。rawData 支持多行，一行一条，
 * 每行按「claude账号----邮箱账号----邮箱密码----SK----ID」（2 个及以上连字符为分隔）拆段。
 */
@Data
public class ClaudeParseRequest {

    /** 原始信息（支持多行，一行一条） */
    @NotBlank(message = "rawData 不能为空")
    private String rawData;
}
