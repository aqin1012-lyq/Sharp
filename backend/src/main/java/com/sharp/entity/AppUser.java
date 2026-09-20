package com.sharp.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 应用登录用户。 */
@Data
@Entity
@Table(name = "app_user", indexes = {
        @Index(name = "uk_username", columnList = "username", unique = true)
})
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录名（唯一） */
    @Column(name = "username", length = 64, nullable = false, unique = true)
    private String username;

    /** BCrypt 密码哈希 */
    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    @CreationTimestamp
    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;
}
