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
