package com.sharp.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Apple 转发邮箱：一个 Apple ID 可以录多个，其中一个标记为「当前转发邮箱」。
 * 同一 Apple ID 下只允许有一条 isCurrent=true，由 AppleService 保证。
 */
@Data
@Entity
@Table(name = "apple_forward_email", indexes = {
        @Index(name = "idx_apple_account_id", columnList = "apple_account_id"),
        @Index(name = "idx_forward_email", columnList = "forward_email")
})
public class AppleForwardEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属 Apple ID（apple_account.id） */
    @Column(name = "apple_account_id", nullable = false)
    private Long appleAccountId;

    /** 转发邮箱 */
    @Column(name = "forward_email", length = 255, nullable = false)
    private String forwardEmail;

    /** 是否当前转发邮箱 */
    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = false;

    /** 备注 */
    @Column(name = "note", length = 1024)
    private String note;

    /** 录入人（取自登录账号） */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /** 所属 Apple ID 账号，列表展示用；不落库（由 service 回填）。 */
    @Transient
    private String appleId;

    /** 该转发邮箱下已录的隐藏邮箱数，列表展示用；不落库。 */
    @Transient
    private Long hideEmailCount;
}
