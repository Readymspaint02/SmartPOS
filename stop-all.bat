@echo off
chcp 65001 >nul
echo ========================================
echo   SmartMarket All Stop
echo ========================================
echo.

echo [1/5] Stopping Frontend (5173)...
taskkill /FI "WINDOWTITLE eq Frontend*" /F /T >nul 2>&1

echo [2/5] Stopping Backend Spring Boot (8081)...
taskkill /FI "WINDOWTITLE eq Backend*" /F /T >nul 2>&1

echo [3/5] Stopping MCP Server (9000/8101/8201/8202)...
taskkill /FI "WINDOWTITLE eq Agent-Proxy*" /F /T >nul 2>&1
taskkill /FI "WINDOWTITLE eq Member-8201*" /F /T >nul 2>&1
taskkill /FI "WINDOWTITLE eq Order-8202*" /F /T >nul 2>&1
taskkill /FI "WINDOWTITLE eq TTS-8101*" /F /T >nul 2>&1

echo [4/5] Stopping Elasticsearch (9200)...
taskkill /FI "WINDOWTITLE eq Elasticsearch*" /F /T >nul 2>&1

echo [5/5] Stopping RabbitMQ (5672/15672)...
taskkill /FI "WINDOWTITLE eq RabbitMQ*" /F /T >nul 2>&1

echo.
echo --- 端口兜底：杀掉关窗口后残留的孤儿进程（java/python/erl/node）---
call :kill_by_port 5173
call :kill_by_port 8081
call :kill_by_port 9000
call :kill_by_port 8101
call :kill_by_port 8201
call :kill_by_port 8202
call :kill_by_port 9200
goto :end

:kill_by_port
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%1 " ^| findstr LISTENING') do (
    taskkill /PID %%a /F >nul 2>&1
)
goto :eof

:end
echo.
echo ========================================
echo   All Services Stopped
echo ========================================
echo.
echo 检查残留端口（应全为空）:
netstat -ano | findstr "LISTENING" | findstr ":5173 :8081 :9000 :8101 :8201 :8202 :9200" 2>nul
echo （无输出=已清理干净）
echo.
pause
