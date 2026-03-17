@echo off
echo Starting Yu AI Agent...

echo Starting backend...
start "Backend" cmd /k "mvn spring-boot:run"

timeout /t 5 /nobreak

echo Starting frontend...
start "Frontend" cmd /k "cd yu-ai-agent-frontend && npm run dev"

echo Both services started!
echo Close the command windows to stop the services.
