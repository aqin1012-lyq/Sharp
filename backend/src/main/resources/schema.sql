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

-- Apple 隐藏邮箱表（子模块二）：属于某个 Apple ID，并记录走的是哪个转发邮箱。
CREATE TABLE IF NOT EXISTS `apple_hide_email` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `apple_account_id` BIGINT       NOT NULL COMMENT '所属 Apple ID(apple_account.id)',
    `forward_email_id` BIGINT       DEFAULT NULL COMMENT '所属转发邮箱(apple_forward_email.id)',
    `hide_email`       VARCHAR(255) NOT NULL COMMENT '隐藏邮箱',
    `created_by`       VARCHAR(64)  DEFAULT NULL COMMENT '录入人(登录账号)',
    `create_time`      DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time`      DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_hide_email` (`hide_email`),
    KEY `idx_apple_account_id` (`apple_account_id`),
    KEY `idx_forward_email_id` (`forward_email_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Apple 隐藏邮箱表';

-- Apple 转发邮箱表（子模块一）：一个 Apple ID 可录多个，其中一个 is_current=1 为当前在用。
CREATE TABLE IF NOT EXISTS `apple_forward_email` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `apple_account_id` BIGINT        NOT NULL COMMENT '所属 Apple ID(apple_account.id)',
    `forward_email`    VARCHAR(255)  NOT NULL COMMENT '转发邮箱',
    `is_current`       TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否当前转发邮箱',
    `note`             VARCHAR(1024) DEFAULT NULL COMMENT '备注',
    `created_by`       VARCHAR(64)   DEFAULT NULL COMMENT '录入人(登录账号)',
    `create_time`      DATETIME      DEFAULT NULL COMMENT '创建时间',
    `update_time`      DATETIME      DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account_forward` (`apple_account_id`, `forward_email`),
    KEY `idx_apple_account_id` (`apple_account_id`),
    KEY `idx_forward_email` (`forward_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Apple 转发邮箱表';

-- Apple 黑客邮箱表（子模块三）：与隐藏邮箱一对一，两个唯一键就是这个约束本身。
CREATE TABLE IF NOT EXISTS `apple_hacker_email` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `hide_email_id` BIGINT       NOT NULL COMMENT '隐藏邮箱(apple_hide_email.id)',
    `hacker_email`  VARCHAR(255) NOT NULL COMMENT '黑客邮箱',
    `created_by`    VARCHAR(64)  DEFAULT NULL COMMENT '录入人(登录账号)',
    `create_time`   DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time`   DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_hide_email_id` (`hide_email_id`),
    UNIQUE KEY `uk_hacker_email` (`hacker_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Apple 黑客邮箱表(与隐藏邮箱一对一)';

-- Apple 接码 / 切换 / 检测日志（模块四 / 五）：一张表三类事件，由 type 区分。
CREATE TABLE IF NOT EXISTS `apple_code_log` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `type`             VARCHAR(32)   NOT NULL COMMENT '事件: fetch(接码) / switch(切换) / check(检测)',
    `entry_email`      VARCHAR(255)  DEFAULT NULL COMMENT '本次入口: 隐藏邮箱 / 黑客邮箱地址',
    `apple_account_id` BIGINT        DEFAULT NULL COMMENT '所属 Apple ID',
    `hide_email_id`    BIGINT        DEFAULT NULL COMMENT '隐藏邮箱',
    `forward_email_id` BIGINT        DEFAULT NULL COMMENT '转发邮箱',
    `forward_email`    VARCHAR(255)  DEFAULT NULL COMMENT '当时的转发邮箱(快照)',
    `success`          TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否成功',
    `verify_code`      VARCHAR(32)   DEFAULT NULL COMMENT '取到的验证码',
    `message`          VARCHAR(1024) DEFAULT NULL COMMENT '失败原因 / 切换说明',
    `duration_ms`      INT           DEFAULT NULL COMMENT '耗时(毫秒)',
    `created_by`       VARCHAR(64)   DEFAULT NULL COMMENT '操作人(登录账号)',
    `create_time`      DATETIME      DEFAULT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_type` (`type`),
    KEY `idx_forward_email_id` (`forward_email_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Apple 接码/切换/检测日志';


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
-- [2026-09-28] Apple 模块拆成三个子模块（转发邮箱 / 隐藏邮箱 / 黑客邮箱），已应用到生产库：
-- 原先 apple_hide_email 一张表里既存转发邮箱(google_alias_email)又存黑客邮箱(redirect_email)，
-- 现拆出 apple_forward_email 与 apple_hacker_email 两张表。迁移步骤（已执行）：
--   CREATE TABLE apple_hide_email_bak_20260928 AS SELECT * FROM apple_hide_email;
--   -- 建上面两张新表后：
--   ALTER TABLE apple_hide_email
--       ADD COLUMN forward_email_id BIGINT DEFAULT NULL AFTER apple_account_id,
--       ADD KEY idx_forward_email_id (forward_email_id);
--   INSERT INTO apple_forward_email (apple_account_id, forward_email, is_current, created_by, create_time, update_time)
--       SELECT apple_account_id, google_alias_email, 0, MIN(created_by), MIN(create_time), NOW()
--       FROM apple_hide_email WHERE google_alias_email <> '' GROUP BY apple_account_id, google_alias_email;
--   -- 每个 Apple ID 下被最新隐藏邮箱使用的那个转发邮箱置 is_current=1，再回填 forward_email_id
--   INSERT INTO apple_hacker_email (hide_email_id, hacker_email, created_by, create_time, update_time)
--       SELECT id, redirect_email, created_by, create_time, NOW()
--       FROM apple_hide_email WHERE redirect_email <> '';
--   ALTER TABLE apple_hide_email
--       DROP KEY idx_redirect_email, DROP KEY idx_google_alias_email,
--       DROP COLUMN redirect_email, DROP COLUMN google_alias_email;
