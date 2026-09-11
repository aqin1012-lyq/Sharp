-- Sharp 管理平台 - 数据库表结构设计
-- JPA 已配置 ddl-auto=update 会自动建表，这里作为设计参考 / 手动初始化脚本。

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
    `recovery_key`   VARCHAR(512) DEFAULT NULL COMMENT 'key / 备用邮箱密码(gmail)',
    `reg_year`       VARCHAR(16)  DEFAULT NULL COMMENT '注册年份(gmail)',
    `country`        VARCHAR(64)  DEFAULT NULL COMMENT '国家(gmail)',
    `auth_key`       VARCHAR(512) DEFAULT NULL COMMENT '辅助验证码 / 2FA 备用码(gmail)',
    `extra_url`      VARCHAR(1024) DEFAULT NULL COMMENT '附加链接: gmail 链接 / 012e 取件链接',
    `refresh_token`  TEXT         DEFAULT NULL COMMENT 'refresh token(outlook)',
    `client_id`      VARCHAR(128) DEFAULT NULL COMMENT 'client id(outlook)',
    `note`           VARCHAR(1024) DEFAULT NULL COMMENT '备注 / 说明(outlook)',
    `cookie`         TEXT         DEFAULT NULL COMMENT 'cookie(outlook)',
    `raw_data`       TEXT         DEFAULT NULL COMMENT '原始信息',
    `create_time`    DATETIME     DEFAULT NULL COMMENT '创建时间',
    `update_time`    DATETIME     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_email_type` (`email_type`),
    KEY `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮箱账号表';
