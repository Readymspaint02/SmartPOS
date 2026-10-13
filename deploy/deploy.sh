#!/bin/bash
# ============================================================
# 极限压缩部署：2核2G 一键部署
# 用法：bash deploy.sh
# ============================================================
set -e

echo "============================================"
echo " SmartMarket Lite Deploy (2C2G)"
echo "============================================"

# 1. 准备环境变量
if [ ! -f .env ]; then
    cp .env.lite .env
    echo "[OK] 已生成 .env（请先编辑填入 HUAWEI_ASR_AK/SK 等密钥）"
else
    echo "[OK] .env 已存在"
fi

# 2. 构建并启动（后台）
echo "[1/3] 构建并启动容器..."
docker compose -f docker-compose.lite.yml up -d --build

# 3. 等待就绪
echo "[2/3] 等待服务就绪..."
sleep 15

# 4. 健康检查
echo "[3/3] 健康检查:"
docker compose -f docker-compose.lite.yml ps

echo ""
echo "============================================"
echo " 部署完成！访问："
echo "   前端:      http://服务器IP/"
echo "   后端API:   http://服务器IP:8081/api"
echo "   AI网关:    :9000 (Agent) / :8101 (TTS)"
echo ""
echo " 常用命令："
echo "   看日志:  docker compose -f docker-compose.lite.yml logs -f"
echo "   重启:    docker compose -f docker-compose.lite.yml restart"
echo "   停止:    docker compose -f docker-compose.lite.yml down"
echo "============================================"
