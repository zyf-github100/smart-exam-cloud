param(
    [string]$NacosAddr = "http://192.168.242.10:8848",
    [string]$Group = "DEFAULT_GROUP",
    [string]$Namespace = "",
    [string]$Username = "",
    [string]$Password = ""
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$dataIds = @(
    "common.yaml",
    "gateway-service.yaml",
    "auth-service.yaml",
    "user-service.yaml",
    "question-service.yaml",
    "exam-service.yaml",
    "grading-service.yaml",
    "analysis-service.yaml",
    "admin-service.yaml"
)

$token = ""
if ($Username -and $Password) {
    try {
        $loginResp = Invoke-RestMethod -Method Post -Uri "$NacosAddr/nacos/v1/auth/login" -Body @{
            username = $Username
            password = $Password
        }
        if ($loginResp.accessToken) {
            $token = $loginResp.accessToken
            Write-Host "Nacos login success."
        }
    } catch {
        Write-Warning "Nacos login failed, continue without token: $($_.Exception.Message)"
    }
}

foreach ($dataId in $dataIds) {
    $filePath = Join-Path $scriptDir $dataId
    if (-not (Test-Path $filePath)) {
        throw "Config file not found: $filePath"
    }

    $content = Get-Content -Raw -Encoding UTF8 $filePath
    $body = @{
        dataId  = $dataId
        group   = $Group
        type    = "yaml"
        content = $content
    }

    if ($Namespace) {
        $body["tenant"] = $Namespace
    }
    if ($token) {
        $body["accessToken"] = $token
    }

    $resp = Invoke-RestMethod -Method Post -Uri "$NacosAddr/nacos/v1/cs/configs" -Body $body
    if ("$resp" -ne "true") {
        throw "Import failed: $dataId, response=$resp"
    }
    Write-Host "Imported: $dataId"
}

Write-Host "All nacos configs imported."
