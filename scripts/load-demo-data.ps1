param(
    [switch]$Local
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$sqlFile = Join-Path $Root 'scripts\demo-data.sql'
$envFile = Join-Path $Root '.env'

if (-not (Test-Path -LiteralPath $sqlFile)) {
    throw "未找到演示数据脚本：$sqlFile"
}

$values = @{}
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^\s*#' -or $line -match '^\s*$') { continue }
        if ($line -match '^\s*([^=]+)=(.*)$') {
            $values[$matches[1].Trim()] = $matches[2].Trim().Trim('"').Trim("'")
        }
    }
}

function Get-Value([string]$Name, [string]$Default) {
    if ($values.ContainsKey($Name) -and $values[$Name]) { return $values[$Name] }
    return $Default
}

$database = Get-Value 'MYSQL_DATABASE' 'mallagent'
$user = Get-Value 'MYSQL_USER' 'mallagent'
$password = Get-Value 'MYSQL_PASSWORD' 'mallagent'

if (-not $Local) {
    $container = (& docker ps --filter 'name=^mallagent-mysql$' --format '{{.Names}}') 2>$null
    if (-not $container) {
        throw "mallagent-mysql 容器未运行。请先启动 Docker，或使用 -Local 连接本机 MySQL。"
    }
    $fileName = 'docker'
    $arguments = "exec -i -e MYSQL_PWD=$password mallagent-mysql mysql --default-character-set=utf8mb4 -u$user $database"
    $label = "Docker MySQL ($database)"
} else {
    $hostName = Get-Value 'MYSQL_HOST' '127.0.0.1'
    $port = Get-Value 'MYSQL_PORT' '3306'
    $env:MYSQL_PWD = $password
    $fileName = 'mysql'
    $arguments = "--default-character-set=utf8mb4 -h$hostName -P$port -u$user $database"
    $label = "本地 MySQL $hostName`:$port/$database"
}

$startInfo = New-Object System.Diagnostics.ProcessStartInfo
$startInfo.FileName = $fileName
$startInfo.Arguments = $arguments
$startInfo.UseShellExecute = $false
$startInfo.RedirectStandardInput = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true

Write-Host "正在向 $label 初始化演示商品..." -ForegroundColor Cyan
$process = [System.Diagnostics.Process]::Start($startInfo)
$sqlBytes = [System.Text.Encoding]::UTF8.GetBytes([System.IO.File]::ReadAllText($sqlFile, [System.Text.Encoding]::UTF8))
$process.StandardInput.BaseStream.Write($sqlBytes, 0, $sqlBytes.Length)
$process.StandardInput.BaseStream.Flush()
$process.StandardInput.Close()
$stdout = $process.StandardOutput.ReadToEnd()
$stderr = $process.StandardError.ReadToEnd()
$process.WaitForExit()

if ($stdout) { Write-Host $stdout }
if ($stderr) { Write-Host $stderr -ForegroundColor DarkYellow }
if ($process.ExitCode -ne 0) {
    throw "演示数据初始化失败，退出码 $($process.ExitCode)。"
}

# 分类树有 Redis 缓存；演示分类直接写库后清理缓存，避免首页等待 TTL 过期。
$redisContainer = (& docker ps --filter 'name=^mallagent-redis$' --format '{{.Names}}') 2>$null
if ($redisContainer) {
    & docker exec mallagent-redis redis-cli DEL 'mallagent:product:category:tree:v1' *> $null
}

Write-Host "演示数据初始化完成。重复执行不会重复创建商品或增加库存。" -ForegroundColor Green
