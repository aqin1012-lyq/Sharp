#!/usr/bin/env bash
# 一键启动本地开发环境：后端 Spring Boot(:8080) + 前端 Vite(:5173)。
# 编排脚本，本身不含敏感信息，可提交到 Git。
# - 后端沿用 run-backend.sh（含数据库连接信息与 JDK 17 设置，已 gitignore）。
# - 前端首次运行会自动 npm install。
# 用法：./run-dev.sh      停止：Ctrl+C（前后端一并关闭）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

# 退出时清理整个进程组：前端、后端及其子进程（mvn/java、vite/node）一起关
cleanup() {
  trap - EXIT INT TERM
  echo ""
  echo "🛑 正在停止前后端…"
  kill 0 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# —— 前置检查 ——
if [[ ! -f "./run-backend.sh" ]]; then
  echo "❌ 缺少 run-backend.sh（含数据库连接信息，需本地自备）。"
  echo "   参考 README.md / docs/DATABASE.md 创建后再运行本脚本。"
  exit 1
fi

if [[ ! -d "frontend/node_modules" ]]; then
  echo "📦 未发现 frontend/node_modules，先安装前端依赖…"
  (cd frontend && npm install)
fi

# —— 启动后端 ——
echo "🚀 启动后端（Spring Boot :8080）…"
./run-backend.sh &
BACKEND_PID=$!

# —— 启动前端 ——
echo "🚀 启动前端（Vite :5173）…"
(cd frontend && npm run dev) &
FRONTEND_PID=$!

echo ""
echo "————————————————————————————————————————"
echo "  后端 API : http://localhost:8080"
echo "  前端页面 : http://localhost:5173"
echo "  邮件取件 : http://localhost:5173/mail-reader"
echo "  按 Ctrl+C 停止前后端"
echo "————————————————————————————————————————"

# 任一进程退出即结束（bash 3.2 无 wait -n，用轮询兼容）；随后触发 cleanup 关闭另一个
while kill -0 "$BACKEND_PID" 2>/dev/null && kill -0 "$FRONTEND_PID" 2>/dev/null; do
  sleep 1
done

echo ""
echo "⚠️  检测到有服务已退出（后端未起通常是 SSH 隧道未开，见上方提示），即将停止另一个。"
