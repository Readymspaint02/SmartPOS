# 智慧超市多模态智能体副屏

面向超市收银场景的前后端分离应用：提供商品、库存、订单、会员、支付和经营报表等功能，并通过语音识别、人脸核验及 AI 助手扩展收银副屏交互。

> 本仓库包含应用代码和开发/部署配置。运行前请自行准备数据库、Redis、RabbitMQ 及所需的第三方服务凭据。不要提交真实密钥、证书、生产数据或个人资料。

## 📌 版本演进

| 版本 | 分支 | 说明 |
|------|------|------|
| v0.1.x | [`legacy-v0.1`](../../tree/legacy-v0.1) | 中软杯参赛版历史（原 supermarket-cashier 仓库，保留完整提交历史） |
| v0.2.x+ | [`main`](../../tree/main) | 当前主版本，持续迭代 |

> **v0.2 在 v0.1 基础上持续演进：**
> - 全面工程化重构，认证升级为 **Shiro + JWT 无状态**
> - **RabbitMQ 异步化**（会员积分异步计算）+ **Redis + Redisson + 乐观锁防超卖**
> - 支付宝当面付沙箱 **验签 + 分布式锁防回调重入**
> - MCP/AI 多智能体（Agent / TTS / ASR / 会员订单查询）
> - Docker Compose 一键部署

> 📦 旧版仓库已归档：[supermarket-cashier](https://github.com/Readymspaint02/supermarket-cashier)（历史版本，只读）

## 项目组成

| 模块 | 说明 | 主要技术 |
| --- | --- | --- |
| `gbs_commerce_system/` | 业务后端：认证、商品、库存、订单、会员、支付、报表、文件及语音接口 | Java 17、Spring Boot 3.4.3、MyBatis-Plus、Shiro/JWT |
| `gbs_commerce_system_ui/` | 收银和管理端前端，另含 MCP 服务 | Vue 3、Vite 7、Element Plus |
| `gbs_commerce_system_ui/mcp_server/` | AI Agent、TTS、ASR 和会员/订单查询等 MCP/HTTP 服务 | Python |
| `ai-assistant-service/` | 独立的 AI 对话与会话服务 | Spring Boot |
| `MYSQL/` | 数据库初始化、迁移和性能分析 SQL；使用前需检查是否含真实业务数据 | MySQL 8 |
| `docker-compose.yml` | MySQL、Redis、RabbitMQ、后端、前端和 MCP 服务编排 | Docker Compose |

## 功能概览

- 用户登录、JWT 认证及基于角色/菜单的权限控制。
- 商品与分类管理、库存校验、订单和支付流程。
- 会员管理与查询、销售报表及 AI 报表助手。
- 语音识别和语音播报服务接入；可配置人脸认证、支付宝支付等能力。
- Redis 缓存、Redisson 分布式锁、RabbitMQ 异步消息及 Prometheus 指标端点。
- MCP/AI 服务支持对话代理，以及会员、订单等业务查询工具。

第三方能力需使用者自行开通并配置凭据；不同部署配置下可启用的功能可能不同。

## 技术栈

- **后端：** Java 17、Spring Boot 3.4.3、MyBatis-Plus 3.5.7、Shiro 1.13.0、JWT 0.11.5、MySQL、Redis、RabbitMQ、Redisson。
- **前端：** Node.js 20.19+（或 22.12+）、Vue 3、Vite 7、Element Plus、Axios。
- **MCP 服务：** Python 及 `gbs_commerce_system_ui/mcp_server/requirements.txt` 中的依赖。
- **本地基础设施：** MySQL 8、Redis 7、RabbitMQ 3。

## 本地开发

### 1. 配置依赖服务和凭据

复制根目录模板并填写本机配置（PowerShell）：

```powershell
Copy-Item .env.example .env
```

按需复制 `gbs_commerce_system_ui/.env.example` 为 `gbs_commerce_system_ui/.env`，以及 `gbs_commerce_system_ui/mcp_server/.env.example` 为 `gbs_commerce_system_ui/mcp_server/.env`。后端配置模板见 `gbs_commerce_system/src/main/resources/application-example.yml`；本地运行时应通过环境变量或未跟踪的本机配置提供数据库口令、JWT 密钥和第三方 API 凭据。模板中的 `CHANGE_ME` 仅是占位符，不能直接用于生产。

### 2. 初始化数据库

准备 MySQL 8 数据库后，检查 `MYSQL/` 中适用于当前版本的建库/迁移脚本，再按项目部署环境执行。不要直接将生产数据库导出文件作为初始化数据提交或公开。

### 3. 启动后端

```powershell
cd gbs_commerce_system
mvn spring-boot:run
```

默认服务端口为 `8081`，上下文路径为 `/api`。部分 Huawei SIS SDK 依赖在 `pom.xml` 中通过本地 `systemPath` 引用；若本机未准备相应 SDK JAR，Maven 构建可能失败。请通过官方渠道获取并按项目引用路径配置，不要提交未经许可的二进制文件。

### 4. 启动前端

```powershell
cd gbs_commerce_system_ui
npm install
npm run dev
```

Vite 默认开发服务地址通常为 `http://localhost:5173`；API 代理和 AI 服务地址按本机环境变量及 `vite.config.js` 配置调整。生产构建命令为 `npm run build`。

### 5. Docker Compose（可选）

在填写根目录 `.env` 并确认数据库初始化脚本、端口和凭据后，从仓库根目录执行：

```powershell
docker compose up -d --build
```

查看状态和日志：

```powershell
docker compose ps
docker compose logs -f backend
```

停止服务：

```powershell
docker compose down
```

Compose 文件中的数据库/消息队列默认值仅适用于本地开发。部署前必须替换为独立的强凭据，并避免将 `.env` 放入版本控制。

## MCP 服务

安装 Python 环境后，在 `gbs_commerce_system_ui/mcp_server/` 安装 `requirements.txt` 中的依赖，并根据 `.env.example` 模板提供数据库、AI/TTS/ASR 等服务配置。可使用目录内的 `start_local.bat` 启动本地服务；具体启用的服务与配置项以该目录代码和部署方式为准。

## 安全与发布说明

- `.env.example`、`application-example.yml` 等模板只包含变量名、非敏感示例或 `CHANGE_ME` 占位符。请勿将真实配置复制进模板。
- `.env`、私钥证书、日志、数据库转储、上传文件、构建产物和个人文档不应加入公开仓库。
- 当前工作副本的历史中曾跟踪环境配置和私钥证书；仅修改当前文件或添加模板不会清除旧提交中的内容。公开或继续推送前应轮换曾使用过的凭据，并制定历史清理方案。
- `MYSQL/` 下的 SQL 文件以及部署文档也应在公开前检查，确认不含生产数据、口令或内部地址。

## 常用验证

```powershell
# 后端测试
cd gbs_commerce_system
mvn test

# 前端生产构建
cd gbs_commerce_system_ui
npm run build
```
