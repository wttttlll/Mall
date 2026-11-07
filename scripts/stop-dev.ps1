param(
    [switch]$StopInfrastructure
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root '.run'

function Stop-TrackedProcess {
    param([string]$Name)

    $pidFile = Join-Path $RunDir "$Name.pid"
    if (-not (Test-Path -LiteralPath $pidFile)) {
        return
    }

    $processId = 0
    try {
        $processId = [int](Get-Content -LiteralPath $pidFile -ErrorAction Stop)
    } catch {
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
        Write-Warning "$Name PID 文件无效，已移除。"
        return
    }

    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $processId" -ErrorAction SilentlyContinue
    if (-not $process) {
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
        Write-Host "$Name 进程已不存在。" -ForegroundColor Yellow
        return
    }

    $escapedRoot = [regex]::Escape($Root)
    $commandLine = [string]$process.CommandLine
    $isMallAgent = ($commandLine -match $escapedRoot) -or
        ($commandLine -match 'mallagent-0\.0\.1-SNAPSHOT\.jar') -or
        ($commandLine -match 'vite')
    if (-not $isMallAgent) {
        Write-Warning "$Name PID $processId 不是可确认的 MallAgent 进程，未停止。请手动检查。"
        return
    }

    Write-Host "正在停止 $Name (PID $processId)..." -ForegroundColor Cyan
    & taskkill /PID $processId /T /F *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "停止 $Name 失败，请手动检查 PID $processId。"
    }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    Write-Host "$Name 已停止。" -ForegroundColor Green
}

Stop-TrackedProcess 'backend'
Stop-TrackedProcess 'frontend'

if ($StopInfrastructure) {
    Push-Location $Root
    try {
        Write-Host "正在停止 MallAgent Docker 基础设施（不会删除容器或数据卷）..." -ForegroundColor Cyan
        & docker compose stop mysql redis rabbitmq
        if ($LASTEXITCODE -ne 0) { throw "停止 Docker 基础设施失败。" }
        Write-Host "Docker 基础设施已停止，数据卷保留。" -ForegroundColor Green
    } finally {
        Pop-Location
    }
} else {
    Write-Host "Docker 基础设施未停止；如需停止，请使用 -StopInfrastructure。" -ForegroundColor DarkGray
}

foreach ($port in 8080, 5173) {
    $conn = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
    if ($conn) {
        Write-Warning "端口 $port 仍被占用，可能是由 IDEA、npm run dev 等外部方式启动的 MallAgent 服务，脚本未处理。"
    }
}
