#Requires -Version 5.1
<#
.SYNOPSIS
  Start shop-ai-v2 stack with an explicit project directory and .env file.

.EXAMPLE
  .\scripts\docker-up.ps1
  .\scripts\docker-up.ps1 -ResetData
#>
param(
  [switch]$ResetData,
  [switch]$Build
)

$ErrorActionPreference = "Stop"
$ProjectDir = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $ProjectDir "docker-compose.yml"))) {
  # allow calling from repo root: scripts\docker-up.ps1 where PSScriptRoot is scripts
  if (Test-Path (Join-Path (Get-Location) "docker-compose.yml")) {
    $ProjectDir = (Get-Location).Path
  }
}
$EnvFile = Join-Path $ProjectDir ".env"
$ComposeFile = Join-Path $ProjectDir "docker-compose.yml"

if (-not (Test-Path $EnvFile)) {
  throw ".env not found: $EnvFile (copy .env.example to .env first)"
}
if (-not (Test-Path $ComposeFile)) {
  throw "docker-compose.yml not found: $ComposeFile"
}

function Get-DotEnvValue([string]$Path, [string]$Key) {
  foreach ($line in Get-Content -LiteralPath $Path) {
    $trim = $line.Trim()
    if (-not $trim -or $trim.StartsWith("#")) { continue }
    $idx = $trim.IndexOf("=")
    if ($idx -lt 1) { continue }
    $k = $trim.Substring(0, $idx).Trim()
    if ($k -eq $Key) {
      return $trim.Substring($idx + 1)
    }
  }
  return $null
}

foreach ($required in @("DB_NAME", "DB_USER", "DB_PASSWORD", "JWT_SECRET")) {
  $value = Get-DotEnvValue $EnvFile $required
  if ([string]::IsNullOrWhiteSpace($value)) {
    throw "Required env '$required' is missing/empty in $EnvFile"
  }
}

$kakaoEnabled = (Get-DotEnvValue $EnvFile "KAKAO_ENABLED")
if ($kakaoEnabled -and $kakaoEnabled.Trim().ToLowerInvariant() -eq "true") {
  foreach ($required in @("KAKAO_CLIENT_ID", "KAKAO_CLIENT_SECRET", "FRONTEND_URL", "OAUTH_PUBLIC_CALLBACK_BASE")) {
    $value = Get-DotEnvValue $EnvFile $required
    if ([string]::IsNullOrWhiteSpace($value)) {
      throw "KAKAO_ENABLED=true but '$required' is missing/empty in $EnvFile"
    }
  }
  Write-Host "Kakao OAuth : enabled (client id/secret present)"
} else {
  Write-Host "Kakao OAuth : disabled (KAKAO_ENABLED is not true)"
}

Write-Host "ProjectDir : $ProjectDir"
Write-Host "EnvFile    : $EnvFile"
Write-Host ("Image tags : backend=" + (Get-DotEnvValue $EnvFile "BACKEND_IMAGE_TAG") + " frontend=" + (Get-DotEnvValue $EnvFile "FRONTEND_IMAGE_TAG"))
Write-Host "DB_NAME/USER loaded (password not printed)"

$composeArgs = @(
  "--project-directory", $ProjectDir,
  "--env-file", $EnvFile,
  "-f", $ComposeFile
)

if ($ResetData) {
  Write-Host "Stopping stack and removing project volumes..."
  & docker.exe compose @composeArgs down -v --remove-orphans
}

# Always recreate so newly added OAuth env vars are injected into running containers.
$upArgs = @("up", "-d", "--remove-orphans", "--force-recreate")
if ($Build) { $upArgs = @("up", "-d", "--build", "--remove-orphans", "--force-recreate") }

& docker.exe compose @composeArgs @upArgs
if ($LASTEXITCODE -ne 0) {
  throw "docker compose failed with exit code $LASTEXITCODE"
}

Write-Host "Waiting for services..."
Start-Sleep -Seconds 8
& docker.exe compose @composeArgs ps

if ($kakaoEnabled -and $kakaoEnabled.Trim().ToLowerInvariant() -eq "true") {
  $backendEnv = & docker.exe compose @composeArgs exec -T backend sh -c "printenv KAKAO_ENABLED" 2>$null
  if (($backendEnv | Out-String).Trim() -ne "true") {
    throw "Kakao env was not injected into backend container. Re-run with an up-to-date docker-compose.yml and .env (KAKAO_ENABLED=true)."
  }
  Write-Host "Kakao OAuth : confirmed inside backend container"
}
