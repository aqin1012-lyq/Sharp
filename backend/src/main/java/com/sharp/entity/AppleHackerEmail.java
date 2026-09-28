package com.sharp.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Apple 黑客邮箱：与隐藏邮箱一对一。
 * 两个唯一键（hide_email_id、hacker_email）就是「一对一」的约束本身。
 */
@Data
@Entity
@Table(name = "apple_hacker_email")
public class AppleHackerEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对应的隐藏邮箱（apple_hide_email.id），唯一 */
    @Column(name = "hide_email_id", nullable = false, unique = true)
    private Long hideEmailId;

    /** 黑客邮箱，唯一 */
    @Column(name = "hacker_email", length = 255, nullable = false, unique = true)
    private String hackerEmail;

    /** 录入人（取自登录账号） */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /** 对应的隐藏邮箱地址，列表展示用；不落库（由 service 回填）。 */
    @Transient
    private String hideEmail;

    /** 隐藏邮箱所属的 Apple ID，列表展示用；不落库。 */
    @Transient
    private String appleId;
}
