$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot

Push-Location $Root
try {
    & docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Desktop 未运行或 Docker 引擎不可用。"
    }
    Write-Host "正在停止 MallAgent Docker 服务（保留容器和数据卷）..." -ForegroundColor Cyan
    & docker compose stop
    if ($LASTEXITCODE -ne 0) {
        throw "停止 Docker 服务失败。"
    }
} finally {
    Pop-Location
}

Write-Host "MallAgent Docker 服务已停止，数据卷和容器未被删除。" -ForegroundColor Green
Write-Host "重新启动：powershell -ExecutionPolicy Bypass -File .\scripts\start-docker.ps1"
