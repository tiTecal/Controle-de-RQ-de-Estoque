# Roda no servidor 192.168.7.3 (PowerShell como Administrador) para atualizar via git pull + NSSM.
param(
    [string]$ServiceName = 'TecalAlmoxarifadoBFF',
    [string]$NssmPath = 'nssm',
    [int]$Port = 8080
)
$ErrorActionPreference = 'Stop'

$bffDir = Split-Path $PSScriptRoot -Parent
$repoDir = Split-Path $bffDir -Parent
$nssmExe = (Get-Command $NssmPath -ErrorAction Stop).Source

Push-Location $repoDir
git pull --ff-only
if ($LASTEXITCODE) { throw 'git pull falhou' }
Pop-Location

Push-Location $bffDir
npm ci
if ($LASTEXITCODE) { throw 'npm ci falhou' }
npm run build
if ($LASTEXITCODE) { throw 'npm run build falhou' }
Pop-Location

& $nssmExe restart $ServiceName
Start-Sleep -Seconds 4
Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/health" | ConvertTo-Json
