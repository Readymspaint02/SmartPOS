@echo off
chcp 65001 >nul
echo ========================================
echo   SmartMarket All Start
echo ========================================
echo.

set PROJECT_ROOT=D:\software\Idea_Project\boss_01_gbs_commerce_system\supermarket-cashier
set BACKEND_PATH=%PROJECT_ROOT%\gbs_commerce_system
set FRONTEND_PATH=%PROJECT_ROOT%\gbs_commerce_system_ui
set MCP_PATH=%FRONTEND_PATH%\mcp_server
set RABBITMQ_PATH=D:\software\yjx_zip\RabbitMQ Server\rabbitmq_server-4.2.4\sbin
set ES_PATH=D:\software\yjx_zip\elasticsearch-7.17.16\bin

echo [1/5] Starting RabbitMQ...
if exist "%RABBITMQ_PATH%\rabbitmq-server.bat" (
    start "RabbitMQ" cmd /k "cd /d %RABBITMQ_PATH% && rabbitmq-server.bat"
    echo [OK] RabbitMQ starting...
    timeout /t 5 >nul
) else (
    echo [WARN] RabbitMQ not found, skip
)

echo.
echo [2/5] Starting Elasticsearch...
if exist "%ES_PATH%\elasticsearch.bat" (
    start "Elasticsearch" cmd /k "cd /d %ES_PATH% && elasticsearch.bat"
    echo [OK] Elasticsearch starting... Port 9200
    timeout /t 3 >nul
) else (
    echo [WARN] Elasticsearch not found, skip
)

echo.
echo [3/5] Starting MCP Server...
if exist "%MCP_PATH%\start_local.bat" (
    cd /d "%MCP_PATH%"
    call start_local.bat
    echo [OK] MCP Server starting...
) else (
    echo [WARN] MCP Server not found, skip
)

echo.
echo [4/5] Starting Backend Spring Boot...
cd /d "%BACKEND_PATH%"
start "Backend" cmd /k "mvn spring-boot:run"
echo [OK] Backend starting... Port 8081
timeout /t 3 >nul

echo.
echo [5/5] Starting Frontend Vue...
cd /d "%FRONTEND_PATH%"
start "Frontend" cmd /k "pnpm dev"
echo [OK] Frontend starting... Port 5173

echo.
echo ========================================
echo   All Core Services Started!
echo ========================================
echo.

REM ============ 可选服务：AI 助手微服务 (ai-assistant-service, :8900) ============
REM 说明：纯聊天 + RAG 知识库问答；不启动也不影响收银主链路和语音下单
REM       语音一句话下单走 Python Agent_HTTP_Proxy:9000，与此服务无关
REM 主流做法：本地开发不需要纯聊天时跳过；要演示 RAG/会话记忆时再启动
set /p START_AI="启动 AI 助手微服务 (端口8900, 可选) ? [y/N] "
if /i "%START_AI%"=="y" (
    echo.
    echo [Optional] Starting AI Assistant Service...
    if exist "%PROJECT_ROOT%\start-ai-service.bat" (
        call "%PROJECT_ROOT%\start-ai-service.bat"
    ) else (
        echo [WARN] start-ai-service.bat not found, skip
    )
) else (
    echo [SKIP] AI Service skipped (enter N or empty). 收银主链路不受影响。
)

echo.
echo URLs:
echo   Frontend:      http://localhost:5173
echo   Backend:       http://localhost:8081/api
echo   RabbitMQ:      http://localhost:15672
echo   Elasticsearch: http://localhost:9200
echo   Nacos(手动启):  http://localhost:8848/nacos  (nacos/nacos)
echo   AI-Service(可选): http://localhost:8900  (需上面输入 y 启动)
echo.
pause
