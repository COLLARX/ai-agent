#!/bin/bash

echo "Starting Yu AI Agent..."

# 启动后端
echo "Starting backend..."
mvn spring-boot:run &
BACKEND_PID=$!

# 等待后端启动
sleep 5

# 启动前端
echo "Starting frontend..."
cd yu-ai-agent-frontend
npm run dev &
FRONTEND_PID=$!

echo "Backend PID: $BACKEND_PID"
echo "Frontend PID: $FRONTEND_PID"
echo "Press Ctrl+C to stop both services"

# 等待用户中断
wait
