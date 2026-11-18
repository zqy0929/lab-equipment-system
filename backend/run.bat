@echo off
rem Start backend (packaged jar, no Maven needed)
rem If backend code changed, repackage first: mvn package -DskipTests
set "JAVA_HOME=D:\jdk\jdk-17.0.20.1+1"
set "SERVER_PORT=8081"
cd /d %~dp0
if exist "%~dp0..\env.local.bat" call "%~dp0..\env.local.bat"
echo [INFO] Starting backend on port 8081 ...
echo [INFO] Keep this window open. Frontend: cd frontend ^&^& npm run dev
"%JAVA_HOME%\bin\java.exe" -jar target\lab-equipment-1.0.0.jar
pause
