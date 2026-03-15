param(
    [Parameter(Mandatory = $true)]
    [string]$Service
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runtimeFile = Join-Path $root ".env.runtime"

if (-not (Test-Path $runtimeFile)) {
    throw "Missing runtime file: $runtimeFile"
}

Get-Content $runtimeFile | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#")) {
        return
    }
    $pair = $line.Split("=", 2)
    if ($pair.Length -eq 2 -and $pair[1].Length -gt 0) {
        [System.Environment]::SetEnvironmentVariable($pair[0], $pair[1], "Process")
    }
}

Push-Location $root
try {
    mvn -f "services/$Service/pom.xml" -am spring-boot:run
}
finally {
    Pop-Location
}
