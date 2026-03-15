param(
    [ValidateSet('backend', 'frontend', 'both')]
    [string]$Target = 'both',
    [switch]$Build,
    [string]$BackendPostCommand = '',
    [string]$FrontendPostCommand = ''
)

$pythonScript = Join-Path $PSScriptRoot 'deploy-smart-exam.py'
$argsList = @($pythonScript, $Target)
if ($Build) {
    $argsList += '--build'
}
if ($BackendPostCommand) {
    $argsList += @('--backend-post-command', $BackendPostCommand)
}
if ($FrontendPostCommand) {
    $argsList += @('--frontend-post-command', $FrontendPostCommand)
}

& python @argsList
