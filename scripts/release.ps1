# backend-next build & push script (run locally on Windows).
#
# Usage:  .\scripts\release.ps1
# Steps:  read scripts/IMAGE_VERSION and bump +1 -> build image -> tag vN + latest -> push to ACR
# Prereq: docker login done once (docker login --username=... <registry>)
# NOTE:   keep this file ASCII-only (Windows PowerShell 5.1 reads BOM-less files as ANSI).
$ErrorActionPreference = "Stop"

# ===== Config =====
$Registry  = "crpi-oyq5ia6dv9vty8p3.cn-shanghai.personal.cr.aliyuncs.com"
$Namespace = "qqt7"
$Repo      = "qqt_repo"
# ==================

$Root = Split-Path $PSScriptRoot -Parent
$VersionFile = Join-Path $PSScriptRoot "IMAGE_VERSION"
$Version = if (Test-Path $VersionFile) { [int](Get-Content $VersionFile -Raw).Trim() } else { 0 }
$Version += 1
Set-Content -Path $VersionFile -Value $Version -NoNewline

$Image = "$Registry/$Namespace/$Repo"
Write-Host "==> building ${Image}:v${Version} (+ latest)"
docker build -t "${Image}:v${Version}" -t "${Image}:latest" `
  --build-arg NPM_REGISTRY=https://registry.npmmirror.com `
  -f "$Root\backend-next\Dockerfile" "$Root\backend-next"
if ($LASTEXITCODE -ne 0) { throw "docker build failed" }

Write-Host "==> pushing v$Version"
docker push "${Image}:v${Version}"
if ($LASTEXITCODE -ne 0) { throw "push failed: run 'docker login $Registry' first" }
Write-Host "==> pushing latest"
docker push "${Image}:latest"

Write-Host ""
Write-Host "Done: ${Image}:v${Version}"
Write-Host "Deploy on server: ./deploy.sh v$Version"
