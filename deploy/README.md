# 后端 + 前端部署到服务器（DB 走 localhost）

Spring Boot 后端与前端静态站点部署到与 MySQL 同一台服务器（`2.25.157.9`），
数据库通过 `127.0.0.1` 本地连接，彻底避免跨境高延迟 / 空闲掉线。

## 线上现状（截至部署时）

```
浏览器 ──HTTPS(自签)+密码──> Nginx:8443 ──┬─ 静态站点 /www/wwwroot/sharp
                                          └─ /api 反代 ──> 后端 127.0.0.1:8090 ──> MySQL 127.0.0.1:3306
```

- **访问地址**：`https://2.25.157.9:8443`（自签证书，首次访问浏览器提示不受信任，点「继续」即可）
- **登录**：Basic Auth，用户名 `admin`，密码见 `/etc/sharp/htpasswd`（哈希，明文只在创建时给出一次）
- **端口占用说明**：该服务器 8080 被另一 docker 生产应用 `sub2api` 占用，80/443 已有其他站点，
  故本项目后端用 **8090**、前端 HTTPS 用 **8443**，均避开现有服务。
- Nginx 由**宝塔面板**管理：`/www/server/nginx`，vhost 目录 `/www/server/panel/vhost/nginx/*.conf`。

关键路径 / 端口一览：

| 用途 | 位置 / 端口 |
|---|---|
| 后端 jar | `/opt/sharp/sharp-backend.jar` |
| 后端环境变量（DB 连接 + 端口，不入库） | `/etc/sharp/backend.env`（600） |
| systemd 服务 | `/etc/systemd/system/sharp-backend.service` |
| 后端监听 | `127.0.0.1:8090`（`SERVER_PORT`） |
| 前端静态目录 | `/www/wwwroot/sharp` |
| 前端 Nginx vhost | `/www/server/panel/vhost/nginx/sharp.conf` |
| 前端监听 | `0.0.0.0:8443`（HTTPS + Basic Auth） |
| 自签证书 | `/etc/sharp/tls/sharp.crt` / `sharp.key` |
| Basic Auth 口令 | `/etc/sharp/htpasswd` |

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
EOF
sudo chmod 600 /etc/sharp/backend.env
sudo chown root:root /etc/sharp/backend.env
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

## 二、前端部署（Nginx + 自签 HTTPS + Basic Auth）

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

### 3. 自签证书 + Basic Auth（首次，已完成）

```bash
# 自签证书（绑 IP，825 天）
mkdir -p /etc/sharp/tls
openssl req -x509 -nodes -newkey rsa:2048 -days 825 \
  -keyout /etc/sharp/tls/sharp.key -out /etc/sharp/tls/sharp.crt \
  -subj "/CN=2.25.157.9" -addext "subjectAltName=IP:2.25.157.9"
chmod 600 /etc/sharp/tls/sharp.key

# Basic Auth 口令（apr1 哈希；下面命令会交互式输入密码两次）
htpasswd -c /etc/sharp/htpasswd admin        # 无 htpasswd 时：printf 'admin:%s\n' "$(openssl passwd -apr1)" > /etc/sharp/htpasswd
chmod 644 /etc/sharp/htpasswd
# 追加更多用户：htpasswd /etc/sharp/htpasswd <用户名>
```

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

    auth_basic "Sharp - restricted";
    auth_basic_user_file /etc/sharp/htpasswd;

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
curl -k -u admin:密码 https://2.25.157.9:8443/                       # 200
curl -k       https://2.25.157.9:8443/                               # 401（未带密码）
curl -k -u admin:密码 "https://2.25.157.9:8443/api/email/list?page=1&size=1"
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
- **口令管理**：`/etc/sharp/htpasswd` 为共享登录；改密 `htpasswd /etc/sharp/htpasswd admin`，加人同理。
- **不要动 8080 / 80 / 443**：分别属于 docker 应用 `sub2api` 与其他既有站点。
- **常用排障**：
  - `systemctl status sharp-backend` / `journalctl -u sharp-backend -n 100`
  - `tail -f /www/wwwlogs/sharp.error.log`
  - 端口占用：`ss -ltnp | grep -E ':8090|:8443'`
```
