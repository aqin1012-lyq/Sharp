# Sharp 管理平台

前后端分离的管理平台。后端 **Java (Spring Boot 3 + JPA)** + **MySQL**，前端 **Vue 3 + Vite + Element Plus**。

当前已实现「**邮箱管理**」模块：选择邮箱类型 → 粘贴原始信息 → 自动按结构拆分 → 表格展示并入库。

## 目录结构

```
Sharp/
├── backend/          # Spring Boot 后端
│   ├── src/main/java/com/sharp/
│   │   ├── entity/            # 实体（email_account 表）
│   │   ├── repository/        # JPA Repository
│   │   ├── service/           # EmailParserService（原始信息拆分）+ EmailAccountService
│   │   ├── controller/        # REST 接口
│   │   ├── dto/ common/ config/
│   │   └── SharpApplication.java
│   └── src/main/resources/
│       ├── application.yml     # 数据库配置（支持环境变量覆盖）
│       └── schema.sql          # 数据库表结构设计（参考 / 手动初始化）
└── frontend/         # Vue 3 前端
    └── src/
        ├── views/EmailManage.vue   # 邮箱管理页面
        ├── views/Dashboard.vue
        ├── layout/Layout.vue        # 侧边栏 + 多页面框架
        ├── api/  router/
        └── main.js
```

## 三种邮箱的原始信息结构

| 类型 | 分隔符 | 字段顺序 |
|------|--------|----------|
| `@gmail.com` | `\|` | 邮箱 \| 密码 \| 备用邮箱 \| key \| 年份 \| 国家 \| 辅助验证码 \| 链接 |
| `@012e.com` | `-{3,}`（容忍 `---`/`----`） | 邮箱 → 密码 → 取件链接 |
| `@outlook.com` | `----` | 邮箱 → 密码 → refreshToken → clientId → 说明 → `cookie` → cookie 值 |

## 数据库表 `email_account`

一张表容纳三种邮箱拆分后的全部字段（不适用的字段留空），并保留 `raw_data` 原始信息。详见 `backend/src/main/resources/schema.sql`。JPA 已配置 `ddl-auto=update`，首次启动会自动建库建表。

## 快速开始

### 1. 后端（需 Java 17+ 与 MySQL）

> 数据库的完整搭建与远程连接流程见 [docs/DATABASE.md](docs/DATABASE.md)。
> 推荐用项目根目录的 `run-backend.sh`（含连接信息，已 gitignore）一键启动。

```bash
cd backend
# 数据库连接可用环境变量覆盖，默认 127.0.0.1:3307 / root / 库名 sharp
export DB_HOST=localhost DB_PORT=3306 DB_NAME=sharp DB_USERNAME=root DB_PASSWORD=你的密码
mvn spring-boot:run
# 启动后监听 http://localhost:8080
```

### 2. 前端（需 Node 18+）

```bash
cd frontend
npm install
npm run dev
# 打开 http://localhost:5173 （已配置 /api 代理到后端 8080）
```

## 主要接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/email/parse` | 解析预览（不入库），支持多行 |
| POST | `/api/email/save`  | 解析并入库 |
| GET  | `/api/email/list`  | 分页查询（`emailType`、`keyword`、`page`、`size`） |
| PUT  | `/api/email/{id}`  | 更新单条 |
| DELETE | `/api/email/{id}` | 删除 |
