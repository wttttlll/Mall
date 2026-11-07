param(
    [switch]$SkipDependencies
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root '.run'
New-Item -ItemType Directory -Force -Path $RunDir | Out-Null

function Get-PortOwner {
    param([int]$Port)
    $conn = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $conn) { return $null }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $($conn.OwningProcess)" -ErrorAction SilentlyContinue
    return [pscustomobject]@{
        Port        = $Port
        ProcessId   = $conn.OwningProcess
        Name        = if ($process) { $process.Name } else { 'unknown' }
        CommandLine = if ($process) { [string]$process.CommandLine } else { '' }
    }
}

function Test-MallAgentOwner {
    param($Owner)
    if (-not $Owner) { return $false }
    $escapedRoot = [regex]::Escape($Root)
    return ($Owner.CommandLine -match $escapedRoot) -or
        ($Owner.CommandLine -match 'mallagent-0\.0\.1-SNAPSHOT\.jar')
}

function Read-DotEnv {
    param([string]$Path)
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*#' -or $line -match '^\s*$') { continue }
        if ($line -match '^\s*([^=]+)=(.*)$') {
            $values[$matches[1].Trim()] = $matches[2].Trim().Trim('"').Trim("'")
        }
    }
    return $values
}

function Get-EnvValue {
    param($Values, [string]$Name, [string]$Default)
    if ($Values.ContainsKey($Name) -and $Values[$Name]) { return $Values[$Name] }
    return $Default
}

function Wait-HttpOk {
    param([string]$Url, [int]$Seconds, [string]$Label)
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) { return }
        } catch {
            # 服务仍在启动，继续等待。
        }
        Start-Sleep -Seconds 2
    }
    throw "$Label 在 $Seconds 秒内未就绪，请查看 .run 目录日志。"
}

$backendOwner = Get-PortOwner 8080
$frontendOwner = Get-PortOwner 5173

if ($backendOwner -and -not (Test-MallAgentOwner $backendOwner)) {
    throw "端口 8080 被其他程序占用：$($backendOwner.Name) (PID $($backendOwner.ProcessId))。脚本不会结束该进程，请先处理端口冲突。"
}
if ($frontendOwner -and -not (Test-MallAgentOwner $frontendOwner)) {
    throw "端口 5173 被其他程序占用：$($frontendOwner.Name) (PID $($frontendOwner.ProcessId))。脚本不会结束该进程，请先处理端口冲突。"
}

if ($backendOwner -and $frontendOwner) {
    Write-Host "MallAgent 后端 (8080) 和前端 (5173) 已在运行，未做任何操作。" -ForegroundColor Green
    Write-Host "前端：http://localhost:5173"
    Write-Host "健康检查：http://localhost:8080/api/v1/system/health"
    exit 0
}

$envFile = Join-Path $Root '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    Copy-Item -LiteralPath (Join-Path $Root '.env.example') -Destination $envFile
    Write-Host "未找到 .env，已从 .env.example 创建本地演示配置。请按需修改 .env。" -ForegroundColor Yellow
}

$envValues = Read-DotEnv -Path $envFile

if (-not $SkipDependencies -and -not $backendOwner) {
    & docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Desktop 未运行或 Docker 引擎不可用。请先启动 Docker Desktop。"
    }
    Write-Host "正在启动 MySQL、Redis、RabbitMQ..." -ForegroundColor Cyan
    Push-Location $Root
    try {
        & docker compose up -d --wait mysql redis rabbitmq
        if ($LASTEXITCODE -ne 0) { throw "Docker Compose 启动基础设施失败。" }
    } finally {
        Pop-Location
    }
}

if (-not $backendOwner) {
    $env:MYSQL_HOST = 'localhost'
    $env:MYSQL_PORT = Get-EnvValue $envValues 'MYSQL_PORT' '3307'
    $env:MYSQL_DATABASE = Get-EnvValue $envValues 'MYSQL_DATABASE' 'mallagent'
    $env:MYSQL_USER = Get-EnvValue $envValues 'MYSQL_USER' 'mallagent'
    $env:MYSQL_PASSWORD = Get-EnvValue $envValues 'MYSQL_PASSWORD' 'mallagent'
    $env:REDIS_HOST = 'localhost'
    $env:REDIS_PORT = Get-EnvValue $envValues 'REDIS_PORT' '6379'
    $env:RABBITMQ_HOST = 'localhost'
    $env:RABBITMQ_PORT = Get-EnvValue $envValues 'RABBITMQ_PORT' '5672'
    $env:RABBITMQ_USER = Get-EnvValue $envValues 'RABBITMQ_USER' 'mallagent'
    $env:RABBITMQ_PASSWORD = Get-EnvValue $envValues 'RABBITMQ_PASSWORD' 'mallagent'
    $env:JWT_SECRET = Get-EnvValue $envValues 'JWT_SECRET' 'mallagent-local-dev-secret-change-me-0123456789abcdef'
    $env:ADMIN_INIT_PASSWORD = Get-EnvValue $envValues 'ADMIN_INIT_PASSWORD' 'Admin@123456'

    Write-Host "正在构建后端..." -ForegroundColor Cyan
    Push-Location $Root
    try {
        & mvn -q -DskipTests package *> (Join-Path $RunDir 'backend-build.log')
        if ($LASTEXITCODE -ne 0) { throw "后端构建失败，请查看 .run\backend-build.log。" }
    } finally {
        Pop-Location
    }

    $jar = Join-Path $Root 'target\mallagent-0.0.1-SNAPSHOT.jar'
    $backend = Start-Process -FilePath 'java' -ArgumentList @('-jar', $jar) `
        -WorkingDirectory $Root `
        -RedirectStandardOutput (Join-Path $RunDir 'backend.log') `
        -RedirectStandardError (Join-Path $RunDir 'backend.err.log') `
        -WindowStyle Hidden -PassThru
    Set-Content -LiteralPath (Join-Path $RunDir 'backend.pid') -Value $backend.Id
    Write-Host "后端已启动，PID $($backend.Id)" -ForegroundColor Green
}

if (-not $frontendOwner) {
    $frontendDir = Join-Path $Root 'frontend'
    if (-not (Test-Path -LiteralPath (Join-Path $frontendDir 'node_modules'))) {
        Write-Host "首次启动前端，正在安装依赖..." -ForegroundColor Cyan
        Push-Location $frontendDir
        try {
            & npm.cmd ci
            if ($LASTEXITCODE -ne 0) { throw "前端依赖安装失败。" }
        } finally {
            Pop-Location
        }
    }
    $vite = Join-Path $frontendDir 'node_modules\vite\bin\vite.js'
    $frontend = Start-Process -FilePath 'node' -ArgumentList @($vite, '--host', '127.0.0.1', '--port', '5173') `
        -WorkingDirectory $frontendDir `
        -RedirectStandardOutput (Join-Path $RunDir 'frontend.log') `
        -RedirectStandardError (Join-Path $RunDir 'frontend.err.log') `
        -WindowStyle Hidden -PassThru
    Set-Content -LiteralPath (Join-Path $RunDir 'frontend.pid') -Value $frontend.Id
    Write-Host "前端已启动，PID $($frontend.Id)" -ForegroundColor Green
}

Wait-HttpOk -Url 'http://localhost:8080/api/v1/system/health' -Seconds 120 -Label '后端'
Wait-HttpOk -Url 'http://localhost:5173' -Seconds 60 -Label '前端'

Write-Host ""
Write-Host "MallAgent 本地开发环境已就绪" -ForegroundColor Green
Write-Host "前端：    http://localhost:5173"
Write-Host "健康检查：http://localhost:8080/api/v1/system/health"
Write-Host "Swagger： http://localhost:8080/swagger-ui.html"
Write-Host "日志：    .run\backend.log、.run\frontend.log"
Write-Host "停止：    powershell -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1"
