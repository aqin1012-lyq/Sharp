# 后端 + 前端部署到服务器（DB 走 localhost）

Spring Boot 后端与前端静态站点部署到与 MySQL 同一台服务器（`2.25.157.9`），
数据库通过 `127.0.0.1` 本地连接，彻底避免跨境高延迟 / 空闲掉线。

## 线上现状（截至部署时）

```
浏览器 ──HTTPS(自签)──> Nginx:8443 ──┬─ 静态站点 /www/wwwroot/sharp
                                     └─ /api 反代 ──> 后端 127.0.0.1:8090 ──> MySQL 127.0.0.1:3306
                                        (鉴权由应用 JWT 承担；注册需邀请码)
```

- **访问地址**：`https://2.25.157.9:8443`（自签证书，首次访问浏览器提示不受信任，点「继续」即可）
- **登录**：应用自带登录/注册（JWT）。首个用户用邀请码注册；邀请码见 `/etc/sharp/backend.env` 的 `AUTH_INVITE_CODE`。
  （早期的 nginx Basic Auth 已移除，改由应用登录接管。）
- **端口占用说明**：该服务器 8080 被另一 docker 生产应用 `sub2api` 占用，80/443 已有其他站点，
  故本项目后端用 **8090**、前端 HTTPS 用 **8443**，均避开现有服务。
- Nginx 由**宝塔面板**管理：`/www/server/nginx`，vhost 目录 `/www/server/panel/vhost/nginx/*.conf`。

关键路径 / 端口一览：

| 用途 | 位置 / 端口 |
|---|---|
| 后端 jar | `/opt/sharp/sharp-backend.jar` |
| 后端环境变量（DB + 端口 + JWT/邀请码，不入库） | `/etc/sharp/backend.env`（600） |
| systemd 服务 | `/etc/systemd/system/sharp-backend.service` |
| 后端监听 | `127.0.0.1:8090`（`SERVER_PORT`） |
| 前端静态目录 | `/www/wwwroot/sharp` |
| 前端 Nginx vhost | `/www/server/panel/vhost/nginx/sharp.conf` |
| 前端监听 | `0.0.0.0:8443`（HTTPS；鉴权走应用 JWT） |
| 自签证书 | `/etc/sharp/tls/sharp.crt` / `sharp.key` |
| 登录用户表 | MySQL `app_user`（BCrypt 密码哈希） |

---

## 一、后端部署

### 1. 本地打包（Mac）

```bash
cd backend
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
mvn clean package -DskipTests
# 产物：backend/target/sharp-backend-1.0.0.jar
```

### 2. 服务器准备（首次，已完成）

```bash
sudo apt-get update && sudo apt-get install -y openjdk-17-jre-headless
sudo useradd -r -s /usr/sbin/nologin sharp-app 2>/dev/null || true
sudo mkdir -p /opt/sharp /etc/sharp
```

### 3. 上传 jar

```bash
scp backend/target/sharp-backend-1.0.0.jar root@2.25.157.9:/opt/sharp/sharp-backend.jar
```

### 4. 数据库连接与端口（服务器，含密码，不入库）

```bash
sudo tee /etc/sharp/backend.env >/dev/null <<'EOF'
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=sharp
DB_USERNAME=sharp
DB_PASSWORD=你的数据库密码
SERVER_PORT=8090
# 登录鉴权（JWT）：secret 用 openssl rand -hex 32 生成；邀请码发给要注册的同事
AUTH_JWT_SECRET=用_openssl_rand_hex_32_生成的随机串
AUTH_INVITE_CODE=你的邀请码
EOF
sudo chmod 600 /etc/sharp/backend.env
sudo chown root:root /etc/sharp/backend.env
```

### 4.1 建表（首次）

表结构由 `deploy/init-db.sql` / `schema.sql` 维护（应用 `ddl-auto=none` 不自动建表）。
登录用户表 `app_user` 首次需手动建：

```bash
set -a; . /etc/sharp/backend.env; set +a
mysql -h127.0.0.1 -u"$DB_USERNAME" -p"$DB_PASSWORD" "$DB_NAME" <<'SQL'
CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  create_time DATETIME DEFAULT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SQL
```

> `application.yml` 的 `datasource` 与 `server.port` 均读环境变量（`DB_*` / `SERVER_PORT`）。
> `ddl-auto=none`，表结构由 `deploy/init-db.sql` / `schema.sql` 维护，应用不自动建表。
> 邮件取件相关（`MAIL_*` / `GMAIL_*`）有默认值，如需覆盖也可写进本文件。

### 5. 安装 systemd 服务并启动

```bash
sudo cp deploy/sharp-backend.service /etc/systemd/system/     # 或 scp 上去
sudo chown -R sharp-app:sharp-app /opt/sharp
sudo systemctl daemon-reload
sudo systemctl enable --now sharp-backend
sudo systemctl status sharp-backend --no-pager
```

### 6. 验证（服务器本地）

```bash
curl -s "http://localhost:8090/api/email/list?page=1&size=2"
journalctl -u sharp-backend --no-pager -n 30 | grep -E "Started SharpApplication|HikariPool"
```

---

## 二、前端部署（Nginx + 自签 HTTPS）

### 1. 本地构建

```bash
cd frontend && npm install && npm run build      # 产物 frontend/dist/
```

前端用 `history` 路由，API 走同源 `/api`，故 Nginx 需 `try_files` 兜底并反代 `/api` 到 8090。

### 2. 上传静态文件

```bash
ssh root@2.25.157.9 'mkdir -p /www/wwwroot/sharp'
scp -r frontend/dist/* root@2.25.157.9:/www/wwwroot/sharp/
```

### 3. 自签证书（首次，已完成）

```bash
mkdir -p /etc/sharp/tls
openssl req -x509 -nodes -newkey rsa:2048 -days 825 \
  -keyout /etc/sharp/tls/sharp.key -out /etc/sharp/tls/sharp.crt \
  -subj "/CN=2.25.157.9" -addext "subjectAltName=IP:2.25.157.9"
chmod 600 /etc/sharp/tls/sharp.key
```

> 鉴权由应用登录（JWT）承担，Nginx 不再配 Basic Auth。

### 4. Nginx vhost（宝塔）

写入 `/www/server/panel/vhost/nginx/sharp.conf`：

```nginx
server {
    listen 8443 ssl;
    server_name _;
    root /www/wwwroot/sharp;
    index index.html;

    ssl_certificate     /etc/sharp/tls/sharp.crt;
    ssl_certificate_key /etc/sharp/tls/sharp.key;
    ssl_protocols TLSv1.2 TLSv1.3;

    location /api/ {
        proxy_pass http://127.0.0.1:8090;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 120s;      # 邮件取件较慢，放宽
        proxy_send_timeout 120s;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }

    access_log /www/wwwlogs/sharp.access.log;
    error_log  /www/wwwlogs/sharp.error.log;
}
```

校验并热加载（`nginx -t` 通过再 reload，配置有错时旧站点不受影响）：

```bash
nginx -t && nginx -s reload
```

### 5. 放行端口

```bash
ufw allow 8443/tcp comment 'sharp https'      # 本机防火墙
# 云安全组：在云控制台放行 8443/TCP（SSH 改不到，需在控制台操作；本机已确认云侧放行）
```

### 6. 验证（外网）

```bash
curl -k -o /dev/null -w '%{http_code}\n' https://2.25.157.9:8443/                     # 200（登录页）
curl -k -o /dev/null -w '%{http_code}\n' "https://2.25.157.9:8443/api/email/list?page=1&size=1"   # 401（未登录）
# 注册（首个用户，需邀请码）→ 拿 token → 带 token 访问：
TOKEN=$(curl -sk -X POST https://2.25.157.9:8443/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"你的密码","inviteCode":"你的邀请码"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
curl -k -H "Authorization: Bearer $TOKEN" "https://2.25.157.9:8443/api/email/list?page=1&size=1"
```

---

## 三、更新发布（以后每次改代码）

后端：

```bash
cd backend && mvn clean package -DskipTests
scp target/sharp-backend-1.0.0.jar root@2.25.157.9:/opt/sharp/sharp-backend.jar
ssh root@2.25.157.9 'chown sharp-app:sharp-app /opt/sharp/sharp-backend.jar && systemctl restart sharp-backend'
```

前端：

```bash
cd frontend && npm run build
ssh root@2.25.157.9 'rm -rf /www/wwwroot/sharp/*'
scp -r frontend/dist/* root@2.25.157.9:/www/wwwroot/sharp/
# 纯静态无需 reload；改了 vhost 才需要 nginx -t && nginx -s reload
```

---

## 备注

- **自签证书**：浏览器首访提示不受信任属正常，点「继续」即可，传输仍加密。日后若有域名，
  可在宝塔为该 vhost 换正式证书（Let's Encrypt）并可改用 443。
- **登录 / 注册（应用 JWT）**：首个用户用邀请码在登录页注册。改动配置后需 `systemctl restart sharp-backend`：
  - 停止注册 / 换邀请码：改 `backend.env` 的 `AUTH_INVITE_CODE`（清空则禁止注册）。
  - 轮换密钥：改 `AUTH_JWT_SECRET`（会使所有已发 token 失效，需重新登录）。
  - 忘记密码：目前无自助找回，直接删对应 `app_user` 行后让其重新注册。
- **「录入统计」按登录账号归属**：后端保存邮箱时把登录用户写入 `created_by`；各人用各自账号登录即可分别统计。
  历史数据无归属，统计里记为「未知」。
- **不要动 8080 / 80 / 443**：分别属于 docker 应用 `sub2api` 与其他既有站点。
- **常用排障**：
  - `systemctl status sharp-backend` / `journalctl -u sharp-backend -n 100`
  - `tail -f /www/wwwlogs/sharp.error.log`
  - 端口占用：`ss -ltnp | grep -E ':8090|:8443'`
```
