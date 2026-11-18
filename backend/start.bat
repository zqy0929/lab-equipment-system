@echo off
rem (Legacy) Build with Maven then run - needs a clean network for Maven downloads
rem Preferred: use run.bat instead
set "JAVA_HOME=D:\jdk\jdk-17.0.20.1+1"
set "SERVER_PORT=8081"
set "Path=%JAVA_HOME%\bin;%Path%"
cd /d %~dp0
if exist "%~dp0..\env.local.bat" call "%~dp0..\env.local.bat"
call mvn spring-boot:run
pause
