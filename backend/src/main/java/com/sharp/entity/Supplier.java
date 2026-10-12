package com.sharp.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 供应商表：按「IP:端口:用户名:密码:地区:到期时间」格式拆分入库。
 * 原始串完整保存在 rawData 以便追溯；到期时间格式不统一，原样存字符串。
 */
@Data
@Entity
@Table(name = "supplier", indexes = {
        @Index(name = "idx_supplier_ip", columnList = "ip"),
        @Index(name = "idx_supplier_region", columnList = "region"),
        @Index(name = "idx_supplier_created_by", columnList = "created_by")
})
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 供应商 IP */
    @Column(name = "ip", length = 255)
    private String ip;

    /** 端口号 */
    @Column(name = "port", length = 32)
    private String port;

    /** 用户名 */
    @Column(name = "username", length = 255)
    private String username;

    /** 密码 */
    @Column(name = "password", length = 255)
    private String password;

    /** 用户地区 */
    @Column(name = "region", length = 128)
    private String region;

    /** 到期时间（原样保存） */
    @Column(name = "expire_time", length = 64)
    private String expireTime;

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
