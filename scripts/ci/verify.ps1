$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

function Invoke-CheckedCommand {
    param(
        [string] $Label,
        [string] $FilePath,
        [string[]] $Arguments,
        [string] $WorkingDirectory = $repoRoot
    )

    Write-Host "==> $Label"
    Push-Location $WorkingDirectory
    try {
        & $FilePath @Arguments
        $exitCode = $LASTEXITCODE
        if ($null -eq $exitCode) {
            $exitCode = 0
        }

        if ($exitCode -ne 0) {
            throw "$Label failed with exit code $exitCode"
        }
    } finally {
        Pop-Location
    }
}

Push-Location $repoRoot
try {
    Invoke-CheckedCommand -Label 'Backend Maven tests' -FilePath 'mvn' -Arguments @('-B', 'test')
    Invoke-CheckedCommand -Label 'Web dependency install' -FilePath 'npm' -Arguments @('ci') -WorkingDirectory (Join-Path $repoRoot 'smart-exam-web')
    Invoke-CheckedCommand -Label 'Web tests' -FilePath 'npm' -Arguments @('run', 'test:ci') -WorkingDirectory (Join-Path $repoRoot 'smart-exam-web')
    Invoke-CheckedCommand -Label 'Web build' -FilePath 'npm' -Arguments @('run', 'build') -WorkingDirectory (Join-Path $repoRoot 'smart-exam-web')
    Invoke-CheckedCommand -Label 'Miniapp static checks' -FilePath 'node' -Arguments @('scripts/ci/check-miniapp.js')
    Invoke-CheckedCommand -Label 'Flutter dependency install' -FilePath 'flutter' -Arguments @('pub', 'get') -WorkingDirectory (Join-Path $repoRoot 'smart-exam-flutter')
    Invoke-CheckedCommand -Label 'Flutter tests' -FilePath 'flutter' -Arguments @('test') -WorkingDirectory (Join-Path $repoRoot 'smart-exam-flutter')
} finally {
    Pop-Location
}
