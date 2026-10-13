# SmartMarket 极限压缩部署（2核2G）

单机部署 6 个容器：`nginx-main` / `mysql` / `redis` / `backend` / `frontend` / `ai-gateway`（Agent:9000 + TTS:8101）。

- 已砍：Elasticsearch、ai-assistant-service(8900)、Nacos、RabbitMQ、Kibana
- RabbitMQ 移除后：会员积分改为订单支付后**同步计算**（功能不变）
- 预编译部署：后端带 jar、前端带 dist，**服务器不做 maven/npm 编译**

## 目录结构

```text
deploy/
├── docker-compose.yml   # 6 容器编排（2C2G 优化）
├── deploy.sh            # 一键部署脚本
├── .env.example         # 环境变量模板（复制为 .env 后填写）
├── nginx-main.conf      # 多站点分发配置（示例域名，按需替换）
└── sites/               # 静态站点目录（可选）
```

## 部署

```bash
cd deploy
cp .env.example .env
vi .env              # 必填 MYSQL_ROOT_PASSWORD / JWT_SECRET / HUAWEI_ASR_AK/SK / DEEPSEEK_API_KEY 等
bash deploy.sh
```

## 多站点分发（nginx-main）

`nginx-main.conf` 中的 `*.example.com` 为示例域名，按实际域名替换：

| 域名 | 站点 | 说明 |
|---|---|---|
| `home.example.com` | 作品集主页 | 静态 sites/home |
| `pos.example.com` | 收银系统 | 反代 frontend + backend + /agent + /tts |
| `campus.example.com` | 校园问答 | 静态 sites/campus |
| `art.example.com` | 艺术品平台 | 静态 sites/art |
| `inn.example.com` | 客栈管理 | 静态 sites/inn |

DNS 用一条三级泛解析覆盖全部子域名（如 `*` → 服务器 IP）。

SSL 证书下来后：
1. 证书放 `deploy/ssl/fullchain.pem + privkey.pem`
2. compose 放开 `443:443` 和 `./ssl:/etc/nginx/ssl:ro` 挂载
3. nginx-main.conf 取消 443 段注释 + 80 跳转 443

## 资源分配

| 容器 | 内存上限 |
|---|---|
| mysql | 512m（innodb buffer 128M）|
| backend | 512m（JVM -Xmx384m）|
| ai-gateway | 256m |
| redis | 128m（maxmemory 96mb）|
| frontend | 64m |
| nginx-main | 64m |
| **合计** | **~1.55G**，2G 机器留 ~450M 余量 |

## 常用命令

```bash
docker compose -f docker-compose.yml logs -f gbs-backend   # 后端日志
docker compose -f docker-compose.yml restart                # 重启
docker compose -f docker-compose.yml down                   # 停止
curl http://localhost:9000/health                           # AI 网关健康检查
```

## 排错

- 后端起不来：多半 MySQL 还没就绪，等 30 秒自动重试（restart: always）
- AI 语音下单没反应：`docker logs gbs-ai-gateway` 看是否缺 DEEPSEEK_API_KEY
- 语音识别不通（WS 失败）：检查 .env 的 HUAWEI_ASR_AK/SK 是否填了；浏览器 F12 Network → WS → /shop/api/asr/stream 是否 101
- 前端 404 / 空白：确认访问的是 /shop/ 路径（根路径 / 会自动跳转）
- 内存不够：调小 backend 的 JAVA_TOOL_OPTIONS=-Xmx320m，或加 swap

## 安全

- `.env` 含真实密钥，**不要提交进 Git**
- 3306/6379 **不要**对公网开放（安全组只放行 80、8081、9000/8101 按需）
- 部署前确认所有密钥已从占位符替换为真实值

## 数据备份

```bash
docker exec gbs-mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" gbs_commerce_system_02' > backup.sql
```
