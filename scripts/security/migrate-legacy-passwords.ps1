param(
    [ValidateSet("dry-run", "migrate")]
    [string]$Mode = "dry-run",
    [string]$JdbcUrl = "",
    [string]$DbUser = "",
    [string]$DbPassword = "",
    [int]$BatchSize = 200,
    [int]$SampleSize = 20,
    [int]$Limit = 0,
    [string]$ReportFile = "runtime-logs/password-migration-report.json",
    [string]$RollbackSql = "",
    [switch]$SkipEnvRuntime
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runtimeFile = Join-Path $root ".env.runtime"

function Set-EnvFromFile([string]$Path) {
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            return
        }
        $pair = $line.Split("=", 2)
        if ($pair.Length -eq 2) {
            [System.Environment]::SetEnvironmentVariable($pair[0], $pair[1], "Process")
        }
    }
}

if (-not $SkipEnvRuntime -and (Test-Path $runtimeFile)) {
    Set-EnvFromFile $runtimeFile
}

if (-not $JdbcUrl) {
    if ($env:MYSQL_URL) {
        $JdbcUrl = $env:MYSQL_URL
    }
    else {
        $mysqlHost = if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { "127.0.0.1" }
        $mysqlPort = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { "3306" }
        $JdbcUrl = "jdbc:mysql://${mysqlHost}:${mysqlPort}/user_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
    }
}

if (-not $DbUser) {
    $DbUser = if ($env:MYSQL_USERNAME) { $env:MYSQL_USERNAME } else { "smart_exam_app" }
}

if (-not $DbPassword) {
    $DbPassword = if ($env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD } else { "" }
}

$execArgs = @(
    "--mode=$Mode",
    "--jdbc-url=$JdbcUrl",
    "--db-user=$DbUser",
    "--db-password=$DbPassword",
    "--batch-size=$BatchSize",
    "--sample-size=$SampleSize",
    "--report-file=$ReportFile"
)

if ($Limit -gt 0) {
    $execArgs += "--limit=$Limit"
}

if ($Mode -eq "migrate" -and $RollbackSql) {
    $execArgs += "--rollback-sql=$RollbackSql"
}

Push-Location $root
try {
    mvn -f "services/auth-service/pom.xml" -am -DskipTests compile exec:java `
        "-Dexec.mainClass=com.smart.exam.auth.tool.LegacyPasswordMigrationTool" `
        "-Dexec.args=$($execArgs -join ' ')"
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
}
finally {
    Pop-Location
}
