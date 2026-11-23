# Roda no servidor 192.168.7.3 (PowerShell como Administrador), dentro do repositorio clonado em C:\projetos\Controle-de-RQ-de-Estoque.
# Segue o "Manual de Transferencia de Aplicacoes para Producao e Backup" (secoes 7.2 e 8):
#   - nome do servico: "Projeto de TI - {NomeDoServico}-api"
#   - Path = node.exe | Startup directory = pasta da API | Arguments = dist\server.js | Startup = Automatic
# Requer: Git, Node.js e NSSM (no PATH ou via -NssmPath "C:\nssm-2.24\win64\nssm.exe").
param(
    [string]$ServiceName = 'Projeto de TI - Controle de RQ de Estoque-api',
    [string]$LegacyServiceName = 'TecalAlmoxarifadoBFF',
    [string]$NssmPath = 'nssm',
    [string]$LogsDir = 'C:\projetos\00 - logs\Controle de RQ de Estoque',
    [int]$Port = 7777,
    [switch]$AllowPendingDbPassword
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
$pending = [regex]::Matches((Get-Content $envFile -Raw), '(?m)^[A-Z0-9_]+=ALTERAR_\S*') | ForEach-Object { $_.Value }
if ($AllowPendingDbPassword) { $pending = $pending | Where-Object { $_ -notmatch '^DB_PASSWORD=' } }
if ($pending) {
    Write-Warning "Preencha em $envFile : $($pending -join ', ') e rode este script novamente."
    notepad $envFile
    exit 1
}
if ((Get-Content $envFile -Raw) -match '(?m)^DB_PASSWORD=ALTERAR_') {
    Write-Warning 'DB_PASSWORD ainda pendente: o servico sobe, mas consultas ao SQL Server falharao ate preencher o .env e reiniciar o servico.'
}
# SIDs bem conhecidos (S-1-5-18 = SYSTEM, S-1-5-32-544 = Administradores): funcionam em Windows em qualquer idioma
icacls $envFile /inheritance:r /grant:r '*S-1-5-18:F' '*S-1-5-32-544:F' | Out-Null
if ($LASTEXITCODE) { throw "Falha ao restringir permissoes do $envFile" }

# 2. Remove o servico legado (nome antigo), se existir, para nao disputar a porta
if ($LegacyServiceName -and ($LegacyServiceName -ne $ServiceName) -and (Get-Service -Name $LegacyServiceName -ErrorAction SilentlyContinue)) {
    & $nssmExe stop $LegacyServiceName | Out-Null
    & $nssmExe remove $LegacyServiceName confirm | Out-Null
}

# 3. Se o servico atual ja existe, para antes de mexer nos arquivos (manual, secao 10)
if (Get-Service -Name $ServiceName -ErrorAction SilentlyContinue) {
    & $nssmExe stop $ServiceName | Out-Null
}

# 4. Instala dependencias (instalacao limpa via package-lock.json) e compila TypeScript
Push-Location $bffDir
npm ci
if ($LASTEXITCODE) { throw 'npm ci falhou' }
npm run build
if ($LASTEXITCODE) { throw 'npm run build falhou' }
Pop-Location

# 5. Pasta de logs (padrao do servidor: C:\projetos\00 - logs\{Projeto})
New-Item $LogsDir -ItemType Directory -Force | Out-Null

# 6. Cria ou atualiza o servico no NSSM
if (Get-Service -Name $ServiceName -ErrorAction SilentlyContinue) {
    & $nssmExe set $ServiceName Application $nodeExe | Out-Null
} else {
    & $nssmExe install $ServiceName $nodeExe | Out-Null
}

& $nssmExe set $ServiceName AppDirectory $bffDir | Out-Null
& $nssmExe set $ServiceName AppParameters 'dist\server.js' | Out-Null
& $nssmExe set $ServiceName DisplayName $ServiceName | Out-Null
& $nssmExe set $ServiceName Description 'API Ponte (BFF Node.js) do Controle de Retirada do Estoque Tecal (TOTVS RM)' | Out-Null
& $nssmExe set $ServiceName Start SERVICE_AUTO_START | Out-Null
& $nssmExe set $ServiceName AppStdout (Join-Path $LogsDir 'api.log') | Out-Null
& $nssmExe set $ServiceName AppStderr (Join-Path $LogsDir 'api.error.log') | Out-Null
& $nssmExe set $ServiceName AppRotateFiles 1 | Out-Null
& $nssmExe set $ServiceName AppRotateOnline 1 | Out-Null
& $nssmExe set $ServiceName AppRotateBytes 10485760 | Out-Null
& $nssmExe set $ServiceName AppEnvironmentExtra "NODE_ENV=production" | Out-Null
# Resiliencia: reinicia em qualquer saida, com backoff de 5s; 15s para encerramento limpo (Ctrl+C)
& $nssmExe set $ServiceName AppExit Default Restart | Out-Null
& $nssmExe set $ServiceName AppRestartDelay 5000 | Out-Null
& $nssmExe set $ServiceName AppThrottle 5000 | Out-Null
& $nssmExe set $ServiceName AppStopMethodConsole 15000 | Out-Null
& $nssmExe set $ServiceName AppStdoutCreationDisposition 4 | Out-Null
& $nssmExe set $ServiceName AppStderrCreationDisposition 4 | Out-Null

# 7. Libera porta 7777 somente para a rede interna
Remove-NetFirewallRule -DisplayName 'Tecal Almoxarifado BFF' -ErrorAction SilentlyContinue
New-NetFirewallRule -DisplayName 'Tecal Almoxarifado BFF' -Direction Inbound -Protocol TCP -LocalPort $Port -RemoteAddress LocalSubnet -Action Allow | Out-Null

# 8. Inicia e valida o servico
Start-Service -Name $ServiceName
Start-Sleep -Seconds 5
try {
    $health = Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/health"
    Write-Host "Servico '$ServiceName' online:" -ForegroundColor Green
    $health | ConvertTo-Json
} catch {
    Write-Warning "Health check falhou. Verifique os logs em $LogsDir : $_"
}
