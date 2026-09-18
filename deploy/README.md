# 后端部署到服务器（DB 走 localhost）

将 Spring Boot 后端部署到与 MySQL 同一台服务器，数据库通过 `127.0.0.1` 本地连接，
彻底避免跨境高延迟/空闲掉线问题。

## 架构

```
浏览器 ──> (可选)Nginx:80 ──> 后端 8080 ──> MySQL 127.0.0.1:3306
```

## 一、本地打包（Mac）

```bash
cd backend
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
mvn clean package -DskipTests
# 产物：backend/target/sharp-backend-1.0.0.jar
```

## 二、服务器准备（Ubuntu）

```bash
# 1. 安装 JDK 17 运行时
sudo apt-get update && sudo apt-get install -y openjdk-17-jre-headless
java -version

# 2. 建应用目录与专用运行用户
sudo useradd -r -s /usr/sbin/nologin sharp-app 2>/dev/null || true
sudo mkdir -p /opt/sharp /etc/sharp
```

## 三、上传 jar（Mac → 服务器）

```bash
scp backend/target/sharp-backend-1.0.0.jar root@2.25.157.9:/opt/sharp/sharp-backend.jar
```

## 四、配置数据库连接（服务器，含密码，不入库）

```bash
sudo tee /etc/sharp/backend.env >/dev/null <<'EOF'
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=sharp
DB_USERNAME=sharp
DB_PASSWORD=你的数据库密码
EOF
sudo chmod 600 /etc/sharp/backend.env
sudo chown root:root /etc/sharp/backend.env
```

## 五、安装 systemd 服务并启动

```bash
# 上传/复制本目录的 sharp-backend.service 到 /etc/systemd/system/
sudo cp deploy/sharp-backend.service /etc/systemd/system/    # 或 scp 上去
sudo chown -R sharp-app:sharp-app /opt/sharp
sudo systemctl daemon-reload
sudo systemctl enable --now sharp-backend
sudo systemctl status sharp-backend --no-pager
```

## 六、验证 CRUD（在服务器本地，localhost 极快）

```bash
BASE=http://localhost:8080/api/email
# 增
curl -s -X POST $BASE/save -H 'Content-Type: application/json' \
  -d '{"emailType":"gmail","rawData":"deploy@gmail.com|pw|bak@x.com|k|2023|US|code|https://x"}'
# 查
curl -s "$BASE/list?keyword=deploy&page=1&size=10"
# 改（把 {id} 换成上一步返回的 id）
curl -s -X PUT $BASE/{id} -H 'Content-Type: application/json' -d '{"country":"JP"}'
# 删
curl -s -X DELETE $BASE/{id}
```

## 七、（可选）前端上线

```bash
# 本地构建
cd frontend && npm install && npm run build     # 产物 frontend/dist/
# 上传
scp -r frontend/dist/* root@2.25.157.9:/var/www/sharp/
# 服务器装 Nginx，站点 root 指向 /var/www/sharp，并将 /api 反代到 127.0.0.1:8080
# 安全组放行 80
```

## 更新发布（以后每次改代码）

```bash
cd backend && mvn clean package -DskipTests
scp target/sharp-backend-1.0.0.jar root@2.25.157.9:/opt/sharp/sharp-backend.jar
ssh root@2.25.157.9 'systemctl restart sharp-backend'
```
