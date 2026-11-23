# Roda no servidor 192.168.7.3 (PowerShell como Administrador), dentro do repositorio clonado.
# Requer: Git, Node.js 22+ e NSSM no PATH (ou passe -NssmPath "C:\nssm\win64\nssm.exe").
param(
    [string]$ServiceName = 'TecalAlmoxarifadoBFF',
    [string]$NssmPath = 'nssm',
    [int]$Port = 8080
)
$ErrorActionPreference = 'Stop'

$bffDir = Split-Path $PSScriptRoot -Parent
$nodeExe = (Get-Command node -ErrorAction Stop).Source
$nssmExe = (Get-Command $NssmPath -ErrorAction Stop).Source

# 1. Configura .env a partir do template (sem commitar segredos)
$envFile = Join-Path $bffDir '.env'
if (-not (Test-Path $envFile)) {
    Copy-Item "$PSScriptRoot\.env.production.template" $envFile
}
$content = Get-Content $envFile -Raw
if ($content -match 'BFF_JWT_SECRET=ALTERAR') {
    $bytes = New-Object byte[] 48
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $content = $content -replace 'BFF_JWT_SECRET=ALTERAR\S*', ('BFF_JWT_SECRET=' + [Convert]::ToBase64String($bytes))
    Set-Content $envFile $content -NoNewline
}
if ((Get-Content $envFile -Raw) -match '=ALTERAR_') {
    Write-Warning "Preencha DB_PASSWORD e BFF_ADMIN_PIN_SHA256 em $envFile e rode este script novamente."
    notepad $envFile
    exit 1
}
icacls $envFile /inheritance:r /grant:r "SYSTEM:F" "Administrators:F" | Out-Null

# 2. Instala dependencias e compila TypeScript
Push-Location $bffDir
npm ci
if ($LASTEXITCODE) { throw 'npm ci falhou' }
npm run build
if ($LASTEXITCODE) { throw 'npm run build falhou' }
Pop-Location

# 3. Prepara pasta de logs
$logsDir = Join-Path $bffDir 'logs'
New-Item $logsDir -ItemType Directory -Force | Out-Null

# 4. Cria ou atualiza o servico no NSSM
$existing = Get-Service -Name $ServiceName -ErrorAction SilentlyContinue
if ($existing) {
    & $nssmExe stop $ServiceName | Out-Null
    & $nssmExe set $ServiceName Application $nodeExe | Out-Null
} else {
    & $nssmExe install $ServiceName $nodeExe | Out-Null
}

& $nssmExe set $ServiceName AppDirectory $bffDir | Out-Null
& $nssmExe set $ServiceName AppParameters 'dist\server.js' | Out-Null
& $nssmExe set $ServiceName DisplayName 'Tecal Almoxarifado BFF' | Out-Null
& $nssmExe set $ServiceName Description 'API Ponte (BFF Node.js) do Controle de Retirada do Estoque Tecal (TOTVS RM)' | Out-Null
& $nssmExe set $ServiceName Start SERVICE_AUTO_START | Out-Null
& $nssmExe set $ServiceName AppStdout (Join-Path $logsDir 'stdout.log') | Out-Null
& $nssmExe set $ServiceName AppStderr (Join-Path $logsDir 'stderr.log') | Out-Null
& $nssmExe set $ServiceName AppRotateFiles 1 | Out-Null
& $nssmExe set $ServiceName AppRotateOnline 1 | Out-Null
& $nssmExe set $ServiceName AppRotateBytes 10485760 | Out-Null
& $nssmExe set $ServiceName AppEnvironmentExtra "NODE_ENV=production" | Out-Null

# 5. Libera porta 8080 somente para a rede interna
Remove-NetFirewallRule -DisplayName 'Tecal Almoxarifado BFF' -ErrorAction SilentlyContinue
New-NetFirewallRule -DisplayName 'Tecal Almoxarifado BFF' -Direction Inbound -Protocol TCP -LocalPort $Port -RemoteAddress LocalSubnet -Action Allow | Out-Null

# 6. Inicia e valida o servico
& $nssmExe start $ServiceName
Start-Sleep -Seconds 4
try {
    $health = Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/health"
    Write-Host "Servico $ServiceName online:" -ForegroundColor Green
    $health | ConvertTo-Json
} catch {
    Write-Warning "Health check falhou. Verifique os logs em $logsDir : $_"
}
