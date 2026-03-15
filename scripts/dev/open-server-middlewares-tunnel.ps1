param(
    [string]$ServerHost = "36.137.84.162",
    [string]$ServerUser = "root"
)

$ErrorActionPreference = "Stop"

$sshArgs = @(
    "-o", "ExitOnForwardFailure=yes",
    "-o", "ServerAliveInterval=60",
    "-L", "13306:127.0.0.1:3306",
    "-L", "6379:127.0.0.1:6379",
    "-L", "5672:127.0.0.1:5672",
    "-L", "15672:127.0.0.1:15672",
    "-N",
    "$ServerUser@$ServerHost"
)

Write-Host "Opening SSH tunnels to $ServerUser@$ServerHost"
Write-Host "  local 13306 -> remote 127.0.0.1:3306 (MySQL)"
Write-Host "  local 6379  -> remote 127.0.0.1:6379 (Redis)"
Write-Host "  local 5672  -> remote 127.0.0.1:5672 (RabbitMQ AMQP)"
Write-Host "  local 15672 -> remote 127.0.0.1:15672 (RabbitMQ UI)"
Write-Host ""
Write-Host "Keep this window open while running local backend services."

ssh @sshArgs
