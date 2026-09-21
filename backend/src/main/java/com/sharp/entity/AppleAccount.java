package com.sharp.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Apple ID 账号：一个 Apple ID 下可挂多个隐藏邮箱（{@link AppleHideEmail}）。
 * 只记账号与备注，不存密码 / 2FA。
 */
@Data
@Entity
@Table(name = "apple_account", indexes = {
        @Index(name = "idx_created_by", columnList = "created_by")
})
public class AppleAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Apple ID 账号（唯一） */
    @Column(name = "apple_id", length = 255, nullable = false)
    private String appleId;

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
}
