# 数据库搭建与连接指南

Sharp 平台使用 **MySQL 8**。本文档记录从零在一台服务器上部署 MySQL、并让本地后端远程连接的完整流程,供复现和排障参考。

> 架构:**本地后端(Mac)+ 远程数据库(服务器)**。后端通过环境变量连接远程 MySQL,无需改动 `application.yml` 里的代码。

---

## 一、连接信息一览

| 项 | 值 | 说明 |
|----|----|----|
| Host | 服务器公网 IP | 例:`2.25.157.9` |
| Port | `3306` | MySQL 默认端口 |
| Database | `sharp` | 字符集 `utf8mb4` |
| 应用账号 | `sharp`@`%` | 专用账号,只授权 `sharp` 库 |
| 密码 | 见本地 `run-backend.sh` | **不入库**,勿提交 Git |
| root | `auth_socket` | 仅服务器本地 `sudo mysql` 管理用 |

---

## 二、服务器端:安装 MySQL 8(Ubuntu/Debian)

### 1. （重装场景）彻底卸载旧 MySQL

> 全新服务器可跳过本节。**注意:清理数据目录会删除所有现有数据库数据。**

```bash
sudo systemctl stop mysql 2>/dev/null || sudo systemctl stop mysqld 2>/dev/null
sudo pkill mysqld 2>/dev/null; sudo pkill mysqld_safe 2>/dev/null

sudo apt-get remove --purge -y 'mysql-server*' 'mysql-client*' 'mysql-common*' 'mysql-community*'
sudo apt-get autoremove -y && sudo apt-get autoclean

# 清理数据、日志、配置与源码版残留
sudo rm -rf /var/lib/mysql /var/log/mysql /etc/mysql /var/run/mysqld
sudo rm -f /etc/my.cnf            # 源码版残留,常把 socket 指到 /tmp/mysql.sock,与 apt 版冲突
```

### 2. 安装

```bash
sudo apt-get update
sudo apt-get install -y mysql-server
sudo systemctl enable --now mysql
sudo systemctl status mysql --no-pager
```

### 3. （若启动失败）手动初始化数据目录

启动失败且错误日志出现 `Failed to find valid data directory`,说明数据目录未正确初始化：

```bash
sudo systemctl stop mysql
sudo systemctl reset-failed mysql.service
sudo rm -rf /var/lib/mysql/*
sudo mkdir -p /var/run/mysqld
sudo chown -R mysql:mysql /var/lib/mysql /var/run/mysqld
sudo mysqld --initialize-insecure --user=mysql --datadir=/var/lib/mysql   # 建无密码 root
sudo systemctl start mysql
sudo systemctl status mysql --no-pager
```

---

## 三、服务器端:建库、建用户

全新安装的 MySQL 8 中 `root@localhost` 默认是 `auth_socket`,直接：

```bash
sudo mysql
```

```sql
CREATE DATABASE IF NOT EXISTS `sharp`
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

-- 应用专用账号(密码换成你自己的强密码,建议纯字母数字+#,避免命令行转义问题)
CREATE USER IF NOT EXISTS 'sharp'@'%' IDENTIFIED WITH caching_sha2_password BY '你的密码';
GRANT ALL PRIVILEGES ON `sharp`.* TO 'sharp'@'%';
FLUSH PRIVILEGES;

-- 确认:应看到一行 sharp | % ,plugin=caching_sha2_password
SELECT user, host, plugin, LENGTH(authentication_string) AS pwlen
FROM mysql.user WHERE user='sharp';
```

> 忘记/需重置密码：`ALTER USER 'sharp'@'%' IDENTIFIED WITH caching_sha2_password BY '新密码'; FLUSH PRIVILEGES;`

---

## 四、服务器端:放开远程访问

### 1. 监听地址（默认只听 127.0.0.1）

```bash
grep -rn 'bind-address' /etc/mysql/
sudo sed -i 's/^bind-address.*/bind-address = 0.0.0.0/' /etc/mysql/mysql.conf.d/mysqld.cnf
sudo systemctl restart mysql
sudo ss -lntp | grep 3306        # 确认为 0.0.0.0:3306
```

### 2. 系统防火墙

```bash
sudo ufw allow 3306/tcp 2>/dev/null; sudo ufw status
# 无 ufw 时检查裸 iptables:
sudo iptables -L INPUT -n | head
```

### 3. 云安全组（最易遗漏）

到云厂商控制台放行**入方向 TCP 3306**。来源建议填你本地固定公网 IP，而非 `0.0.0.0/0`。

---

## 五、表结构

后端 JPA 配置 `ddl-auto=none`，**不会自动建表，也不会自动加字段**。表结构由 SQL 脚本手动管理：

- 新库初始化：`deploy/init-db.sql`（建库 + 建账号 + 建表，服务器上 `sudo mysql < /tmp/init-db.sql`）
- 结构参考：`backend/src/main/resources/schema.sql`

**已有库补字段**：`CREATE TABLE IF NOT EXISTS` 对已存在的表不生效，加字段要单独执行 ALTER。当前最新一次变更是 `uuid` / `token`：

```sql
ALTER TABLE `email_account`
    ADD COLUMN `uuid`  VARCHAR(128) DEFAULT NULL COMMENT 'UUID'  AFTER `cookie`,
    ADD COLUMN `token` TEXT         DEFAULT NULL COMMENT 'token' AFTER `uuid`,
    ADD KEY `idx_uuid` (`uuid`);
```

执行后自检列数应为 19：

```sql
SELECT COUNT(*) FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'sharp' AND TABLE_NAME = 'email_account';
```

---

## 六、本地(Mac):启动后端连远程库

`application.yml` 支持环境变量覆盖，用项目根目录的 `run-backend.sh` 注入连接信息（该文件含密码，已在 `.gitignore` 中忽略）：

```bash
# run-backend.sh 内容示意
export DB_HOST=2.25.157.9
export DB_PORT=3306
export DB_NAME=sharp
export DB_USERNAME=sharp
export DB_PASSWORD=你的密码
cd "$(dirname "$0")/backend" && exec mvn spring-boot:run
```

启动：

```bash
./run-backend.sh          # 看到 Started SharpApplication 即成功
```

前端（另开终端）：

```bash
cd frontend && npm install && npm run dev   # http://localhost:5173
```

---

## 七、验证与排障

**验证连通**（本地 Mac）：

```bash
mysql -h 2.25.157.9 -P 3306 -u sharp -p -e "SELECT @@hostname, @@version;"
```

`@@hostname` 显示服务器主机名即表示真连到了服务器。

**验证入库**（前端存一条后）：

```bash
mysql -h 2.25.157.9 -P 3306 -u sharp -p \
  -e "SELECT id,email_type,email,create_time FROM sharp.email_account ORDER BY id DESC LIMIT 5;"
```

**常见问题对照**：

| 现象 | 原因 | 处理 |
|------|------|------|
| `Can't connect / timeout` | 网络层被拦 | 查云安全组 → 系统防火墙 → `bind-address` |
| `Access denied for user 'sharp'` | 密码/授权不符 | 重置密码后用 `-h 127.0.0.1` 在服务器本地先验证 |
| `Can't connect through socket '/tmp/mysql.sock'` | 残留 `/etc/my.cnf` 指错 socket | 删除/改名 `/etc/my.cnf` |
| 启动日志 `Failed to find valid data directory` | 数据目录未初始化 | 见「二.3 手动初始化」 |
| `sudo mysql` 也 Access denied | root 非 auth_socket 且密码未知 | `--skip-grant-tables` 重置 root(见下) |

**重置 root（skip-grant-tables）**：

```bash
sudo systemctl stop mysql
sudo mysqld --skip-grant-tables --skip-networking --user=mysql &
sudo mysql -u root
# SQL: FLUSH PRIVILEGES; ALTER USER 'root'@'localhost' IDENTIFIED WITH auth_socket;
sudo pkill mysqld; sleep 3; sudo systemctl start mysql
```
