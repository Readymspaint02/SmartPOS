@echo off
chcp 65001 >nul
REM ============================================================
REM   AI Assistant Service 启动脚本（可选服务，端口 8900）
REM   调用：start-ai-service.bat
REM   依赖：DEEPSEEK_API_KEY（从 mcp_server\.env.secrets 读取）
REM   说明：纯聊天 + RAG 知识库问答微服务；不启动也不影响收银主链路
REM        与 Python Agent_HTTP_Proxy:9000 是两条独立的 AI 路径
REM ============================================================

set PROJECT_ROOT=%~dp0
set AI_SERVICE_PATH=%PROJECT_ROOT%ai-assistant-service
set ENV_SECRETS=%PROJECT_ROOT%gbs_commerce_system_ui\mcp_server\.env.secrets

REM 从 .env.secrets 读取 DEEPSEEK_API_KEY（按项目约定 key 不硬编码）
set DEEPSEEK_API_KEY=
if exist "%ENV_SECRETS%" (
    for /f "usebackq tokens=1,* delims==" %%a in ("%ENV_SECRETS%") do (
        if /i "%%a"=="DEEPSEEK_API_KEY" set DEEPSEEK_API_KEY=%%b
    )
)

if "%DEEPSEEK_API_KEY%"=="" (
    echo [WARN] DEEPSEEK_API_KEY not found in %ENV_SECRETS%
    echo        ai-service will use placeholder key in application.yml ^(will fail to call DeepSeek^)
    echo        纯聊天功能将不可用，但不影响启动和 RAG 配置加载
) else (
    echo [OK] DEEPSEEK_API_KEY loaded from .env.secrets
)

echo.
echo [AI-Service] Starting ai-assistant-service on port 8900 ...
cd /d "%AI_SERVICE_PATH%"
REM 关键：本机 JVM 默认 file.encoding=GBK，会导致 Nacos 拉取含中文的 ai-assistant.yml 时
REM 报 MalformedInputException 使 Nacos 配置解析失败、降级用本地默认提示词。
REM 用 JAVA_TOOL_OPTIONS 强制 UTF-8，Java 自动拾取，无需改 mvn 引号。
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8
start "AI-Service" cmd /k "set DEEPSEEK_API_KEY=%DEEPSEEK_API_KEY% && set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 && mvn spring-boot:run"
echo [OK] AI-Service starting ... Port 8900
echo.
echo URLs:
echo   AI-Service:   http://localhost:8900
echo   Nacos:         http://localhost:8848/nacos  (nacos/nacos)
echo   AI 聊天 SSE:   POST http://localhost:8900/ai/chat/stream
echo.
pause
