# Roda na maquina de desenvolvimento: testa, compila e gera deploy\tecal-almoxarifado-bff.zip
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
if (-not (Test-Path node_modules)) { npm ci; if ($LASTEXITCODE) { throw 'npm ci falhou' } }
npm test; if ($LASTEXITCODE) { throw 'testes falharam' }
npm run build; if ($LASTEXITCODE) { throw 'build falhou' }
$ProgressPreference = 'SilentlyContinue'

$stage = Join-Path $PSScriptRoot 'stage'
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item $stage -ItemType Directory | Out-Null
Copy-Item dist, package.json, package-lock.json, Dockerfile, docker-compose.yml, tsconfig.json -Destination $stage -Recurse
Copy-Item "$PSScriptRoot\.env.production.template" "$stage\.env.production.template"
Copy-Item "$PSScriptRoot\instalar-windows.ps1" "$stage\instalar-windows.ps1"
Copy-Item "$PSScriptRoot\LEIAME-IMPLANTACAO.md" "$stage\LEIAME-IMPLANTACAO.md"
# src necessario apenas para o build Docker
Copy-Item src -Destination $stage -Recurse

$zip = Join-Path $PSScriptRoot 'tecal-almoxarifado-bff.zip'
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path "$stage\*" -DestinationPath $zip
Remove-Item $stage -Recurse -Force
Write-Host "Pacote gerado: $zip (nenhum .env/segredo incluido)"
