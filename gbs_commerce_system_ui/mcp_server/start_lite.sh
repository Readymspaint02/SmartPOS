#!/bin/sh
# 同一容器内启动两个 Python 网关进程：
#   Agent_HTTP_Proxy :9000  (DeepSeek 智能体 + 会话记忆)
#   TTS_HTTP_Proxy   :8101  (百度 TTS 语音播报)
set -e

echo "[gateway] Starting Agent_HTTP_Proxy on :9000 ..."
python Agent_HTTP_Proxy.py &

echo "[gateway] Starting TTS_HTTP_Proxy on :8101 ..."
python TTS_HTTP_Proxy.py &

echo "[gateway] Both proxies started. Waiting..."
# 任一进程退出则整个容器退出（便于 docker restart 自愈）
wait -n
