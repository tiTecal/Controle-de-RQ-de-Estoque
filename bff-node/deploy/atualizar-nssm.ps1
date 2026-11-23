# Roda no servidor 192.168.7.3 (PowerShell como Administrador) para atualizar via git pull + NSSM.
# Ordem do manual (secao 10): parar servico -> substituir arquivos -> dependencias -> build -> reiniciar -> validar.
param(
    [string]$ServiceName = 'Projeto de TI - Controle de RQ de Estoque-api',
    [string]$NssmPath = 'nssm',
    [int]$Port = 7777
)
$ErrorActionPreference = 'Stop'

$bffDir = Split-Path $PSScriptRoot -Parent
$repoDir = Split-Path $bffDir -Parent
$nssmExe = (Get-Command $NssmPath -ErrorAction Stop).Source

& $nssmExe stop $ServiceName | Out-Null

try {
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
} finally {
    # Sobe o servico mesmo em caso de falha, para nao deixar o tablet sem API (versao anterior em dist\)
    Start-Service -Name $ServiceName
}

Start-Sleep -Seconds 5
Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/health" | ConvertTo-Json
