package com.sharp.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Apple 隐藏邮箱：隐藏邮箱（xxx@privaterelay.appleid.com）同时记录两个转发目标——
 * HackerOne 邮箱与谷歌别名邮箱。与 {@link AppleAccount} 的关联只存 id、不建外键，
 * 与 email_account 的处理保持一致。
 */
@Data
@Entity
@Table(name = "apple_hide_email", indexes = {
        @Index(name = "idx_apple_account_id", columnList = "apple_account_id"),
        @Index(name = "idx_redirect_email", columnList = "redirect_email"),
        @Index(name = "idx_google_alias_email", columnList = "google_alias_email")
})
public class AppleHideEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属 Apple ID（apple_account.id） */
    @Column(name = "apple_account_id", nullable = false)
    private Long appleAccountId;

    /** 隐藏邮箱（唯一） */
    @Column(name = "hide_email", length = 255, nullable = false)
    private String hideEmail;

    /** HackerOne 邮箱（列名沿用建表时的 redirect_email） */
    @Column(name = "redirect_email", length = 255)
    private String redirectEmail;

    /** 谷歌别名邮箱 */
    @Column(name = "google_alias_email", length = 255)
    private String googleAliasEmail;

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
}
