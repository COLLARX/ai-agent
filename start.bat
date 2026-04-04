@echo off
setlocal

echo =====================================
echo   Yu AI Agent Local Startup Script
echo =====================================

set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"
set "FRONTEND_DIR=%ROOT_DIR%\yu-ai-agent-frontend"
set "JAVA_HOME="

for %%J in ("%JAVA21_HOME%" "D:\develop\jdk" "%JAVA_HOME%") do (
  if not defined JAVA_HOME if exist "%%~J\bin\java.exe" (
    "%%~J\bin\java.exe" -version 2>&1 | findstr /c:"21." >nul && set "JAVA_HOME=%%~J"
  )
)

if defined JAVA_HOME (
  set "PATH=%JAVA_HOME%\bin;%PATH%"
  echo [OK] Using Java21: %JAVA_HOME%
) else (
  echo [WARN] Java21 not found. Please set JAVA21_HOME or install Java21 to D:\develop\jdk
  echo [WARN] Backend may fail with UnsupportedClassVersionError.
)

set "SPRING_PROFILES_ACTIVE=local"
set "MAVEN_OPTS=-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8"

echo.
echo [1/2] Starting backend on http://localhost:8523/api
start "YuAI-Backend" cmd /k "chcp 65001>nul && cd /d ""%ROOT_DIR%"" && set SPRING_PROFILES_ACTIVE=local && echo JAVA_HOME=%JAVA_HOME% && java -version && mvn -v && mvn spring-boot:run"

timeout /t 5 /nobreak >nul

echo [2/2] Starting frontend (Vite dev server)
start "YuAI-Frontend" cmd /k "chcp 65001>nul && cd /d ""%FRONTEND_DIR%"" && npm run dev -- --host 0.0.0.0"

echo.
echo Startup commands sent successfully.
echo Backend health:  http://localhost:8523/api/health
echo Frontend page:   http://localhost:3000  (or next available port in frontend window)
echo.
echo Close the two opened terminal windows to stop services.

endlocal
