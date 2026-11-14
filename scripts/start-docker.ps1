$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot

function Get-PortOwner {
    param([int]$Port)
    $conn = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $conn) { return $null }
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $($conn.OwningProcess)" -ErrorAction SilentlyContinue
    return [pscustomobject]@{
        Port      = $Port
        ProcessId = $conn.OwningProcess
        Name      = if ($process) { $process.Name } else { 'unknown' }
    }
}

$envFile = Join-Path $Root '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    Copy-Item -LiteralPath (Join-Path $Root '.env.example') -Destination $envFile
    Write-Host "未找到 .env，已从 .env.example 创建本地演示配置。请按需修改 .env。" -ForegroundColor Yellow
}

$envValues = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*#' -or $line -match '^\s*$') { continue }
    if ($line -match '^\s*([^=]+)=(.*)$') {
        $envValues[$matches[1].Trim()] = $matches[2].Trim().Trim('"').Trim("'")
    }
}

function Get-EnvValue {
    param($Values, [string]$Name, [string]$Default)
    if ($Values.ContainsKey($Name) -and $Values[$Name]) { return $Values[$Name] }
    return $Default
}

$backendPort = [int](Get-EnvValue $envValues 'DOCKER_BACKEND_PORT' '8081')
$managementPort = [int](Get-EnvValue $envValues 'DOCKER_MANAGEMENT_PORT' '8082')
$frontendPort = [int](Get-EnvValue $envValues 'FRONTEND_PORT' '8088')
$mysqlPort = [int](Get-EnvValue $envValues 'MYSQL_PORT' '3307')
$redisPort = [int](Get-EnvValue $envValues 'REDIS_PORT' '6379')
$rabbitPort = [int](Get-EnvValue $envValues 'RABBITMQ_PORT' '5672')
$rabbitManagementPort = [int](Get-EnvValue $envValues 'RABBITMQ_MANAGEMENT_PORT' '15672')

& docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Docker Desktop 未运行或 Docker 引擎不可用。请先启动 Docker Desktop。"
}

$runningNames = @(& docker ps --format '{{.Names}}') 2>$null
foreach ($item in @(
        @{ Port = $backendPort; Container = 'mallagent-backend' },
        @{ Port = $managementPort; Container = 'mallagent-backend' },
        @{ Port = $frontendPort; Container = 'mallagent-frontend' },
        @{ Port = $mysqlPort; Container = 'mallagent-mysql' },
        @{ Port = $redisPort; Container = 'mallagent-redis' },
        @{ Port = $rabbitPort; Container = 'mallagent-rabbitmq' },
        @{ Port = $rabbitManagementPort; Container = 'mallagent-rabbitmq' })) {
    $owner = Get-PortOwner $item.Port
    $running = $runningNames -contains $item.Container
    if ($owner -and -not $running) {
        throw "端口 $($item.Port) 被其他程序占用：$($owner.Name) (PID $($owner.ProcessId))。脚本不会结束该进程，请先处理端口冲突。"
    }
}

Push-Location $Root
try {
    Write-Host "正在构建并启动 MallAgent Docker 完整环境..." -ForegroundColor Cyan
    & docker compose up -d --build --wait
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose 启动失败。请查看 docker compose logs。"
    }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "MallAgent Docker 环境已就绪" -ForegroundColor Green
Write-Host "前端：      http://localhost:$frontendPort"
Write-Host "后端健康：  http://localhost:$backendPort/api/v1/system/health"
Write-Host "管理探针：  http://127.0.0.1:$managementPort/actuator/health/readiness"
Write-Host "Swagger：   http://localhost:$backendPort/swagger-ui.html"
Write-Host "停止：      powershell -ExecutionPolicy Bypass -File .\scripts\stop-docker.ps1"
Write-Host "日志：      docker compose logs -f backend frontend"
