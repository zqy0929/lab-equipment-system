@echo off
setlocal
set "JAVA_HOME=D:\jdk\jdk-17.0.20.1+1"
set "SERVER_PORT=8081"
set "ROOT=%~dp0"
if exist "%ROOT%env.local.bat" call "%ROOT%env.local.bat"

echo ==========================================
echo   Lab Equipment System - One Click Start
echo ==========================================
echo.

if not exist "%ROOT%backend\target\lab-equipment-1.0.0.jar" (
  echo [ERROR] Backend jar not found: backend\target\lab-equipment-1.0.0.jar
  pause
  exit /b 1
)
if not exist "%ROOT%frontend\node_modules" (
  echo [ERROR] Frontend dependencies missing.
  echo Please run: cd frontend ^&^& npm install
  pause
  exit /b 1
)

echo [1/3] Starting backend on port 8081 ...
start "Lab Backend (8081)" cmd /k call "%ROOT%backend\run.bat"
echo       Waiting 12 seconds ...
timeout /t 12 /nobreak >nul

echo [2/3] Starting frontend dev server on port 5173 ...
start "Lab Frontend (5173)" cmd /k "cd /d %ROOT%frontend && npm run dev"
echo       Waiting 12 seconds ...
timeout /t 12 /nobreak >nul

echo [3/3] Opening browser ...
start "" http://localhost:5173

echo.
echo Done. Open http://localhost:5173 and log in with your own account.
echo Keep the two black windows open while using the system.
echo Close those windows to stop the system.
echo.
pause
