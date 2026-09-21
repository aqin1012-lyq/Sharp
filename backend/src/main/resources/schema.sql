-- Sharp 管理平台 - 数据库表结构设计
-- application.yml 为 ddl-auto=none，后端不会自动建表 / 改表，本文件与 deploy/init-db.sql 是表结构的唯一来源。
-- 已有库新增字段请执行文末的 ALTER 语句。

CREATE DATABASE IF NOT EXISTS `sharp` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `sharp`;

-- 邮箱账号表：一张表容纳三种邮箱（gmail / 012e / outlook）拆分后的所有字段。
-- 与当前邮箱类型无关的字段留空即可；原始信息完整保存在 raw_data 便于追溯。
CREATE TABLE IF NOT EXISTS `email_account` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `email_type`     VARCHAR(32)  NOT NULL COMMENT '邮箱类型: gmail / 012e / outlook',
    `email`          VARCHAR(255) DEFAULT NULL COMMENT '邮箱地址',
    `password`       VARCHAR(255) DEFAULT NULL COMMENT '邮箱密码',
    `recovery_email` VARCHAR(255) DEFAULT NULL COMMENT '备用邮箱(gmail)',
    `recovery_key`   TEXT         DEFAULT NULL COMMENT 'key / 备用邮箱密码(gmail)',
    `reg_year`       VARCHAR(16)  DEFAULT NULL COMMENT '注册年份(gmail)',
    `country`        VARCHAR(64)  DEFAULT NULL COMMENT '国家(gmail)',
    `auth_key`       TEXT         DEFAULT NULL COMMENT '辅助验证码 / 2FA 备用码(gmail)',
    `extra_url`      VARCHAR(1024) DEFAULT NULL COMMENT '附加链接: gmail 链接 / 012e 取件链接',
    `refresh_token`  TEXT         DEFAULT NULL COMMENT 'refresh token(outlook)',
    `client_id`      VARCHAR(128) DEFAULT NULL COMMENT 'client id(outlook)',
    `note`           VARCHAR(1024) DEFAULT NULL COMMENT '备注 / 说明(outlook)',
    `cookie`         TEXT         DEFAULT NULL COMMENT 'cookie(outlook)',
    `uuid`           VARCHAR(128) DEFAULT NULL COMMENT 'UUID: 原始串里带则解析，否则留空',
    `token`          TEXT         DEFAULT NULL COMMENT 'token: 原始串里带则解析，否则留空',
    `raw_data`       TEXT         DEFAULT NULL COMMENT '原始信息',
    `created_by`     VARCHAR(64)  DEFAULT NULL COMMENT '录入人(登录账号，nginx X-Auth-User)',
    `create_time`    DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time`    DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_email_type` (`email_type`),
    KEY `idx_email` (`email`),
    KEY `idx_uuid` (`uuid`),
    KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱账号表';

-- 应用登录用户表
CREATE TABLE IF NOT EXISTS `app_user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(64)  NOT NULL COMMENT '登录名',
    `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    `create_time`   DATETIME     DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用登录用户表';

-- Apple ID 账号表：一个 Apple ID 下可挂多个隐藏邮箱。只记账号与备注，不存密码 / 2FA。
CREATE TABLE IF NOT EXISTS `apple_account` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `apple_id`    VARCHAR(255)  NOT NULL COMMENT 'Apple ID 账号',
    `note`        VARCHAR(1024) DEFAULT NULL COMMENT '备注',
    `created_by`  VARCHAR(64)   DEFAULT NULL COMMENT '录入人(登录账号)',
    `create_time` DATETIME      DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME      DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_apple_id` (`apple_id`),
    KEY `idx_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Apple ID 账号表';

-- Apple 隐藏邮箱表：隐藏邮箱(xxx@privaterelay.appleid.com) → 同时记录两个转发目标。
-- 与 apple_account 的关联只建索引不加外键，与 email_account 的处理保持一致。
CREATE TABLE IF NOT EXISTS `apple_hide_email` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `apple_account_id`   BIGINT       NOT NULL COMMENT '所属 Apple ID(apple_account.id)',
    `hide_email`         VARCHAR(255) NOT NULL COMMENT '隐藏邮箱',
    `redirect_email`     VARCHAR(255) DEFAULT NULL COMMENT 'HackerOne 邮箱',
    `google_alias_email` VARCHAR(255) DEFAULT NULL COMMENT '谷歌别名邮箱',
    `created_by`         VARCHAR(64)  DEFAULT NULL COMMENT '录入人(登录账号)',
    `create_time`        DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time`        DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_hide_email` (`hide_email`),
    KEY `idx_apple_account_id` (`apple_account_id`),
    KEY `idx_redirect_email` (`redirect_email`),
    KEY `idx_google_alias_email` (`google_alias_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Apple 隐藏邮箱表';

-- ---------- 已有库的增量迁移（下列 ALTER 截至 2026-09-18 均已应用到生产库）----------
-- 新库直接看上面的 CREATE TABLE 即为最新完整结构；以下仅供已存在的旧库升级参考。
-- MySQL 不支持 ADD COLUMN IF NOT EXISTS，已经加过的话重跑会报 Duplicate column name，可忽略。
--
-- [已应用] 新增 uuid / token 两列：
-- ALTER TABLE `email_account`
--     ADD COLUMN `uuid`  VARCHAR(128) DEFAULT NULL COMMENT 'UUID'  AFTER `cookie`,
--     ADD COLUMN `token` TEXT         DEFAULT NULL COMMENT 'token' AFTER `uuid`,
--     ADD KEY `idx_uuid` (`uuid`);
--
-- [已应用] auth_key / recovery_key 由 VARCHAR(512) 放宽为 TEXT（长 2FA 码 / key 会超 512）：
-- ALTER TABLE `email_account`
--     MODIFY COLUMN `auth_key`     TEXT COMMENT '辅助验证码 / 2FA 备用码(gmail)',
--     MODIFY COLUMN `recovery_key` TEXT COMMENT 'key / 备用邮箱密码(gmail)';
--
-- [2026-09-20] 新增 created_by（录入人统计）：
-- ALTER TABLE `email_account`
--     ADD COLUMN `created_by` VARCHAR(64) DEFAULT NULL COMMENT '录入人(登录账号)' AFTER `raw_data`,
--     ADD KEY `idx_created_by` (`created_by`);
--
-- [2026-09-21] 新增 Apple ID 录入模块：直接执行上面 apple_account / apple_hide_email
-- 两段 CREATE TABLE 即可（含 IF NOT EXISTS，重跑安全）。生产库发版前需先建表。
--
-- [2026-09-21] apple_hide_email 增加 google_alias_email（每条记录同时录 HackerOne 邮箱与谷歌别名邮箱）：
-- ALTER TABLE `apple_hide_email`
--     ADD COLUMN `google_alias_email` VARCHAR(255) DEFAULT NULL
--         COMMENT '谷歌别名邮箱' AFTER `redirect_email`,
--     ADD KEY `idx_google_alias_email` (`google_alias_email`);
-- 同日曾短暂存在过的 `type` 列（区分两类录入）已废弃：
-- ALTER TABLE `apple_hide_email` DROP KEY `idx_type`, DROP COLUMN `type`;
