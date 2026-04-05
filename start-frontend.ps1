$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$frontendDir = Join-Path $projectRoot "yu-ai-agent-frontend"

if (-not (Test-Path $frontendDir)) {
    Write-Error "未找到前端目录: $frontendDir"
}

Set-Location $frontendDir

if (-not (Test-Path (Join-Path $frontendDir "node_modules"))) {
    Write-Host "未检测到 node_modules，正在执行 npm install ..."
    npm install
}

Write-Host "正在启动前端开发服务器 ..."
npm run dev
