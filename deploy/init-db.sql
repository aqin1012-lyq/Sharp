-- ============================================================
-- Sharp 管理平台 - 数据库一键初始化脚本
--
-- 适用场景：服务器上重建数据库，后端与 MySQL 同机部署（走 127.0.0.1:3306）。
-- 执行方式（服务器本地，root 为 auth_socket）：
--     sudo mysql < /tmp/init-db.sql
-- 或进入 mysql 交互后整段粘贴：
--     sudo mysql
--
-- ⚠️ 执行前：确认下面 @APP_PASSWORD 就是你要用的密码，并与 /etc/sharp/backend.env 中的
--    DB_PASSWORD 保持一致。
-- ============================================================

-- ---------- 0. 应用账号密码（改这里） ----------
-- 当前用的是弱密码 'sharp'，安全性完全依赖「MySQL 只监听 127.0.0.1 + 安全组不放 3306」。
-- 一旦哪天对公网开放 3306，必须先把这里改成强密码并同步 /etc/sharp/backend.env。
SET @APP_PASSWORD = 'sharp';

-- ---------- 1. 建库 ----------
CREATE DATABASE IF NOT EXISTS `sharp`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE `sharp`;

-- ---------- 2. 建应用账号并授权 ----------
-- 后端同机部署，只允许本机连接；不再开 'sharp'@'%'（公网 3306 应关闭）。
SET @sql = CONCAT(
    "CREATE USER IF NOT EXISTS 'sharp'@'localhost' ",
    "IDENTIFIED WITH caching_sha2_password BY '", @APP_PASSWORD, "'"
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 账号已存在时（比如重跑脚本/改密码），把密码同步成上面设置的值
SET @sql = CONCAT(
    "ALTER USER 'sharp'@'localhost' ",
    "IDENTIFIED WITH caching_sha2_password BY '", @APP_PASSWORD, "'"
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

GRANT ALL PRIVILEGES ON `sharp`.* TO 'sharp'@'localhost';

-- 历史遗留的远程账号（此前"本地后端连远程库"方案留下的）。同机部署不再需要，
-- 确认本地 Mac 不再直连远程库后，手动执行下面这行清掉：
--   DROP USER IF EXISTS 'sharp'@'%';

FLUSH PRIVILEGES;

-- ---------- 3. 建表 ----------
-- 邮箱账号表：一张表容纳三种邮箱（gmail / 012e / outlook）拆分后的所有字段。
-- 与当前邮箱类型无关的字段留空即可；原始信息完整保存在 raw_data 便于追溯。
-- 注意：application.yml 现为 ddl-auto=none，后端不会自动建表，此脚本是唯一建表入口。
CREATE TABLE IF NOT EXISTS `email_account` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `email_type`     VARCHAR(32)   NOT NULL COMMENT '邮箱类型: gmail / 012e / outlook',
    `email`          VARCHAR(255)  DEFAULT NULL COMMENT '邮箱地址',
    `password`       VARCHAR(255)  DEFAULT NULL COMMENT '邮箱密码',
    `recovery_email` VARCHAR(255)  DEFAULT NULL COMMENT '备用邮箱(gmail)',
    `recovery_key`   TEXT          DEFAULT NULL COMMENT 'key / 备用邮箱密码(gmail)',
    `reg_year`       VARCHAR(16)   DEFAULT NULL COMMENT '注册年份(gmail)',
    `country`        VARCHAR(64)   DEFAULT NULL COMMENT '国家(gmail)',
    `auth_key`       TEXT          DEFAULT NULL COMMENT '辅助验证码 / 2FA 备用码(gmail)',
    `extra_url`      VARCHAR(1024) DEFAULT NULL COMMENT '附加链接: gmail 链接 / 012e 取件链接',
    `refresh_token`  TEXT          DEFAULT NULL COMMENT 'refresh token(outlook)',
    `client_id`      VARCHAR(128)  DEFAULT NULL COMMENT 'client id(outlook)',
    `note`           VARCHAR(1024) DEFAULT NULL COMMENT '备注 / 说明(outlook)',
    `cookie`         TEXT          DEFAULT NULL COMMENT 'cookie(outlook)',
    `uuid`           VARCHAR(128)  DEFAULT NULL COMMENT 'UUID: 原始串里带则解析，否则留空',
    `token`          TEXT          DEFAULT NULL COMMENT 'token: 原始串里带则解析，否则留空',
    `raw_data`       TEXT          DEFAULT NULL COMMENT '原始信息',
    `create_time`    DATETIME      DEFAULT NULL COMMENT '创建时间',
    `update_time`    DATETIME      DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_email_type` (`email_type`),
    KEY `idx_email` (`email`),
    KEY `idx_uuid` (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱账号表';

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


-- ---------- 3.1 已有库的增量迁移（下列 ALTER 截至 2026-09-18 均已应用到生产库）----------
-- 上面的 CREATE TABLE IF NOT EXISTS 对**已存在**的表不生效，老库补列 / 改类型要执行下面这段。
-- MySQL 不支持 ADD COLUMN IF NOT EXISTS，已经加过的话会报 Duplicate column name，可忽略。
--
-- [已应用] 新增 uuid / token 两列：
-- ALTER TABLE `email_account`
--     ADD COLUMN `uuid`  VARCHAR(128) DEFAULT NULL COMMENT 'UUID'  AFTER `cookie`,
--     ADD COLUMN `token` TEXT         DEFAULT NULL COMMENT 'token' AFTER `uuid`,
--     ADD KEY `idx_uuid` (`uuid`);
--
-- [已应用] auth_key / recovery_key 由 VARCHAR(512) 放宽为 TEXT（长 2FA 码 / key 会超 512 触发 Data too long）：
-- ALTER TABLE `email_account`
--     MODIFY COLUMN `auth_key`     TEXT COMMENT '辅助验证码 / 2FA 备用码(gmail)',
--     MODIFY COLUMN `recovery_key` TEXT COMMENT 'key / 备用邮箱密码(gmail)';

-- ---------- 4. 自检 ----------
SELECT SCHEMA_NAME AS db, DEFAULT_CHARACTER_SET_NAME AS charset, DEFAULT_COLLATION_NAME AS collation
FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'sharp';

SELECT user, host, plugin FROM mysql.user WHERE user = 'sharp';

SELECT COUNT(*) AS column_count FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'sharp' AND TABLE_NAME = 'email_account';   -- 期望 19

SELECT COUNT(*) AS row_count FROM `sharp`.`email_account`;        -- 新建库期望 0

-- ============================================================
-- 附：彻底推倒重来（会删光 email_account 全部数据，确认后再手动执行）
--   DROP DATABASE IF EXISTS `sharp`;
--   DROP USER IF EXISTS 'sharp'@'localhost';
-- 然后重跑本脚本。
-- ============================================================
