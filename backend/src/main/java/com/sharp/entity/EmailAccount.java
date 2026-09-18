package com.sharp.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 邮箱账号表：一张表容纳三种邮箱（gmail / 012e / outlook）拆分后的所有字段，
 * 与邮箱类型无关的字段留空即可。原始信息完整保存在 rawData 中以便追溯。
 */
@Data
@Entity
@Table(name = "email_account", indexes = {
        @Index(name = "idx_email_type", columnList = "email_type"),
        @Index(name = "idx_email", columnList = "email"),
        @Index(name = "idx_uuid", columnList = "uuid")
})
public class EmailAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 邮箱类型：gmail / 012e / outlook */
    @Column(name = "email_type", length = 32, nullable = false)
    private String emailType;

    /** 邮箱地址 */
    @Column(name = "email", length = 255)
    private String email;

    /** 邮箱密码 */
    @Column(name = "password", length = 255)
    private String password;

    /** 备用邮箱（gmail） */
    @Column(name = "recovery_email", length = 255)
    private String recoveryEmail;

    /** key / 备用邮箱密码（gmail） */
    @Lob
    @Column(name = "recovery_key", columnDefinition = "TEXT")
    private String recoveryKey;

    /** 注册年份（gmail） */
    @Column(name = "reg_year", length = 16)
    private String regYear;

    /** 国家（gmail） */
    @Column(name = "country", length = 64)
    private String country;

    /** 辅助验证码 / 2FA 备用码（gmail） */
    @Lob
    @Column(name = "auth_key", columnDefinition = "TEXT")
    private String authKey;

    /** 附加链接：gmail 的链接 / 012e 的取件链接 */
    @Column(name = "extra_url", length = 1024)
    private String extraUrl;

    /** refresh token（outlook） */
    @Lob
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    /** client id（outlook） */
    @Column(name = "client_id", length = 128)
    private String clientId;

    /** 备注 / 说明（outlook） */
    @Column(name = "note", length = 1024)
    private String note;

    /** cookie（outlook） */
    @Lob
    @Column(name = "cookie", columnDefinition = "TEXT")
    private String cookie;

    /** UUID */
    @Column(name = "uuid", length = 128)
    private String uuid;

    /** token */
    @Lob
    @Column(name = "token", columnDefinition = "TEXT")
    private String token;

    /** 原始信息 */
    @Lob
    @Column(name = "raw_data", columnDefinition = "TEXT")
    private String rawData;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
