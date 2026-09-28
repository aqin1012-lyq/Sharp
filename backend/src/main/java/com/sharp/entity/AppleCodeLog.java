package com.sharp.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Apple 接码 / 切换 / 检测日志。一张表三类事件，由 {@code type} 区分：
 * fetch=倒推接码、switch=转发邮箱切换、check=可用性检测。
 */
@Data
@Entity
@Table(name = "apple_code_log", indexes = {
        @Index(name = "idx_type", columnList = "type"),
        @Index(name = "idx_forward_email_id", columnList = "forward_email_id"),
        @Index(name = "idx_create_time", columnList = "create_time")
})
public class AppleCodeLog {

    public static final String TYPE_FETCH = "fetch";
    public static final String TYPE_SWITCH = "switch";
    public static final String TYPE_CHECK = "check";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 事件类型：fetch / switch / check */
    @Column(name = "type", length = 32, nullable = false)
    private String type;

    /** 本次入口：隐藏邮箱 / 黑客邮箱地址 */
    @Column(name = "entry_email", length = 255)
    private String entryEmail;

    @Column(name = "apple_account_id")
    private Long appleAccountId;

    @Column(name = "hide_email_id")
    private Long hideEmailId;

    @Column(name = "forward_email_id")
    private Long forwardEmailId;

    /** 当时的转发邮箱地址，快照保存，便于日后查历史 */
    @Column(name = "forward_email", length = 255)
    private String forwardEmail;

    @Column(name = "success", nullable = false)
    private Boolean success = false;

    @Column(name = "verify_code", length = 32)
    private String verifyCode;

    /** 失败原因 / 切换说明 */
    @Column(name = "message", length = 1024)
    private String message;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    /** 关联的 Apple ID 账号，列表展示用；不落库。 */
    @Transient
    private String appleId;
}
