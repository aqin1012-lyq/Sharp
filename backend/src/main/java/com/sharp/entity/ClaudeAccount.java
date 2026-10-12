package com.sharp.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Claude 账号表：按「claude账号----邮箱账号----邮箱密码----SK----ID」格式拆分入库。
 * 原始串完整保存在 rawData 以便追溯。
 */
@Data
@Entity
@Table(name = "claude_account", indexes = {
        @Index(name = "idx_claude_account", columnList = "claude_account"),
        @Index(name = "idx_claude_email", columnList = "email"),
        @Index(name = "idx_claude_created_by", columnList = "created_by")
})
public class ClaudeAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Claude 账号 */
    @Column(name = "claude_account", length = 255)
    private String claudeAccount;

    /** 邮箱账号 */
    @Column(name = "email", length = 255)
    private String email;

    /** 邮箱密码 */
    @Column(name = "email_password", length = 255)
    private String emailPassword;

    /** SK（密钥，可能较长） */
    @Lob
    @Column(name = "sk", columnDefinition = "TEXT")
    private String sk;

    /** ID */
    @Column(name = "account_id", length = 255)
    private String accountId;

    /** 原始信息 */
    @Lob
    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    /** 录入人（取自登录账号） */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
