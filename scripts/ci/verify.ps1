$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

function Invoke-CheckedCommand {
    param(
        [string] $Label,
        [string] $FilePath,
        [string[]] $Arguments
    )

    Write-Host "==> $Label"
    & $FilePath @Arguments

    if ($LASTEXITCODE -ne 0) {
        throw "$Label failed with exit code $LASTEXITCODE"
    }
}

Push-Location $repoRoot
try {
    Invoke-CheckedCommand 'Running backend tests' 'mvn' @('-B', 'test')
    Invoke-CheckedCommand 'Installing frontend dependencies' 'npm' @('--prefix', 'smart-exam-web', 'ci')
    Invoke-CheckedCommand 'Running frontend tests' 'npm' @('--prefix', 'smart-exam-web', 'run', 'test:ci')
    Invoke-CheckedCommand 'Building frontend' 'npm' @('--prefix', 'smart-exam-web', 'run', 'build')
} finally {
    Pop-Location
}
