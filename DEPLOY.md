# 智慧超市智能体 - 部署文档

## 一、架构说明

```
前端(Vue) → Nginx → 后端(SpringBoot) → MySQL
                    ↓
              MCP服务(Python) ← Cherry Studio/DeepSeek
```

**两种使用方式**：
1. **Web前端**：通过 DeepSeek API 直接对话
2. **Cherry Studio**：本地 MCP 服务 + 任意模型

---

## 二、服务器要求

- Linux (Ubuntu/CentOS)
- Docker + Docker Compose
- 2GB+ 内存
- 开放端口：80, 443(可选)

---

## 三、快速部署

### 3.1 上传代码到服务器

```bash
# 在服务器上
mkdir -p /opt/smartmarket
cd /opt/smartmarket

# 上传以下目录：
# - gbs_commerce_system/         (SpringBoot后端)
# - gbs_commerce_system_ui/      (Vue前端 + MCP服务)
# - docker-compose.yml
```

### 3.2 修改配置文件

#### 修改 MCP 服务配置
```bash
cd /opt/smartmarket/gbs_commerce_system_ui/mcp_server
vi .env
```

```ini
# MySQL配置（容器内网络）
MYSQL_HOST=gbs-mysql
MYSQL_PORT=3306
MYSQL_DATABASE=gbs_commerce_system
MYSQL_USER=root
MYSQL_PASSWORD=<CHANGE_ME>

# DeepSeek API（Web前端用）
DEEPSEEK_API_KEY=sk-xxxxxxxxxxxxxxxx
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-chat

# 百度TTS（可选，语音播报用）
BAIDU_TTS_APP_ID=你的AppID
BAIDU_TTS_API_KEY=你的APIKey
BAIDU_TTS_SECRET_KEY=你的SecretKey
```

#### 修改前端环境变量
```bash
cd /opt/smartmarket/gbs_commerce_system_ui
vi .env
```

```ini
# 如果用域名
VITE_AGENT_PROXY_URL=https://你的域名/agent
VITE_TTS_HTTP_URL=https://你的域名/tts
VITE_ASSISTANT_SERVICE_URL=https://你的域名/assistant
VITE_MEMBER_SERVICE_URL=https://你的域名/mcp/member
VITE_ASR_WS_URL=wss://你的域名/ws-asr/

# 如果用IP
VITE_AGENT_PROXY_URL=http://你的IP:9000
VITE_TTS_HTTP_URL=http://你的IP:8101
VITE_ASSISTANT_SERVICE_URL=http://你的IP:8202
VITE_MEMBER_SERVICE_URL=http://你的IP:8201
VITE_ASR_WS_URL=ws://你的IP:8765
```

### 3.3 启动 Docker 服务

```bash
cd /opt/smartmarket
docker-compose up -d
```

等待 MySQL 初始化完成（约30秒）。

### 3.4 启动 MCP 服务

```bash
cd /opt/smartmarket/gbs_commerce_system_ui/mcp_server

# 安装依赖
pip3 install mcp[cli] fastmcp httpx baidu-aip pymysql chardet

# 启动 HTTP 服务（供 Web 前端调用）
./start_all_mcp.sh
```

---

## 四、Cherry Studio 使用（本地 MCP）

### 4.1 下载 Cherry Studio
官网：https://cherry-ai.com

### 4.2 配置 MCP Server

打开 Cherry Studio → 设置 → MCP → 添加服务器：

```json
{
  "mcpServers": {
    "smartmarket": {
      "command": "python3",
      "args": ["Unified_MCP_Service.py"],
      "cwd": "/opt/smartmarket/gbs_commerce_system_ui/mcp_server",
      "env": {
        "MYSQL_HOST": "你的服务器IP",
        "MYSQL_PORT": "3306",
        "MYSQL_DATABASE": "gbs_commerce_system",
        "MYSQL_USER": "root",
        "MYSQL_PASSWORD": "<CHANGE_ME>"
      }
    }
  }
}
```

### 4.3 选择模型
在 Cherry Studio 中选择：
- DeepSeek（推荐）
- 通义千问
- 其他支持的模型

### 4.4 测试对话
```
查询会员 M001 的积分
搜索手机尾号 3333 的会员
查询订单 ORD20250001 的详情
最近7天的销售趋势
```

---

## 五、常用命令

```bash
# 查看 Docker 状态
docker-compose ps

# 查看 MySQL 日志
docker logs gbs-mysql

# 查看后端日志
docker logs gbs-backend

# 查看 MCP 服务日志
tail -f /opt/smartmarket/gbs_commerce_system_ui/mcp_server/logs/*.log

# 停止 MCP 服务
cd /opt/smartmarket/gbs_commerce_system_ui/mcp_server
./stop_all_mcp.sh

# 重启 MCP 服务
./stop_all_mcp.sh && ./start_all_mcp.sh

# 停止所有服务
docker-compose down
```

---

## 六、API 接口说明

| 服务 | 端口 | 接口 | 说明 |
|------|------|------|------|
| Agent Proxy | 9000 | POST /agent_query | DeepSeek 智能对话 |
| TTS | 8101 | POST /tts_synthesize | 语音合成 |
| Member | 8201 | POST /member/get_profile | 查询会员 |
| Member | 8201 | GET /member/search | 搜索会员 |
| Order | 8202 | POST /order/get_detail | 查询订单 |
| Order | 8202 | GET /order/recent | 最近订单 |

---

## 七、常见问题

### Q1: MySQL 连接失败
```bash
# 进入 MySQL 容器
docker exec -it gbs-mysql mysql -uroot -p

# 检查数据库
SHOW DATABASES;
USE gbs_commerce_system;
SHOW TABLES;
```

### Q2: MCP 服务启动失败
```bash
# 检查依赖
pip3 install mcp[cli] fastmcp httpx baidu-aip pymysql chardet

# 手动测试
cd /opt/smartmarket/gbs_commerce_system_ui/mcp_server
python3 Unified_MCP_Service.py stdio
```

### Q3: DeepSeek 调用失败
- 检查 `.env` 中的 `DEEPSEEK_API_KEY`
- 去 https://platform.deepseek.com 获取 API Key

### Q4: 百度 TTS 调用失败
- 检查 `.env` 中的百度配置
- 去 https://ai.baidu.com 注册获取

---

## 八、获取 API Key

### DeepSeek
1. 访问 https://platform.deepseek.com
2. 注册账号
3. 创建 API Key
4. 复制到 `.env` 的 `DEEPSEEK_API_KEY`

### 百度 TTS
1. 访问 https://ai.baidu.com
2. 创建语音合成应用
3. 获取 AppID、API Key、Secret Key
4. 复制到 `.env`

---

## 九、文件清单

```
/opt/smartmarket/
├── docker-compose.yml          # Docker 编排
├── gbs_commerce_system/        # SpringBoot 后端
│   └── Dockerfile
├── gbs_commerce_system_ui/     # Vue 前端 + MCP
│   ├── .env                    # 前端环境变量
│   ├── Dockerfile
│   ├── nginx.conf
│   └── mcp_server/
│       ├── .env                # MCP 环境变量
│       ├── Unified_MCP_Service.py  # 统一 MCP 入口
│       ├── Agent_HTTP_Proxy.py     # DeepSeek 代理
│       ├── start_all_mcp.sh        # 启动脚本
│       └── stop_all_mcp.sh         # 停止脚本
│       └── cherry_studio_config.json
```