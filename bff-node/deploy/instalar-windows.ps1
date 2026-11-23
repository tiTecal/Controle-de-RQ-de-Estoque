# Roda no servidor 192.168.7.3 (Windows, PowerShell como Administrador), dentro da pasta extraida do zip.
# Requer Node.js 22+ instalado. Registra o BFF para iniciar com o Windows e abre a porta 8080 na rede interna.
param([string]$InstallDir = 'C:\Tecal\almoxarifado-bff', [int]$Port = 8080)
$ErrorActionPreference = 'Stop'

if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'Node.js 22+ nao encontrado no servidor.' }

New-Item $InstallDir -ItemType Directory -Force | Out-Null
Copy-Item "$PSScriptRoot\dist", "$PSScriptRoot\package.json", "$PSScriptRoot\package-lock.json" -Destination $InstallDir -Recurse -Force

$envFile = Join-Path $InstallDir '.env'
if (-not (Test-Path $envFile)) { Copy-Item "$PSScriptRoot\.env.production.template" $envFile }
$content = Get-Content $envFile -Raw
if ($content -match 'BFF_JWT_SECRET=ALTERAR') {
    $bytes = New-Object byte[] 48
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $content = $content -replace 'BFF_JWT_SECRET=ALTERAR\S*', ('BFF_JWT_SECRET=' + [Convert]::ToBase64String($bytes))
    Set-Content $envFile $content -NoNewline
}
if ((Get-Content $envFile -Raw) -match '=ALTERAR_') {
    Write-Warning "Preencha os campos ALTERAR_* em $envFile e rode este script novamente."
    notepad $envFile
    exit 1
}
icacls $envFile /inheritance:r /grant:r "SYSTEM:F" "Administrators:F" | Out-Null

Push-Location $InstallDir
npm ci --omit=dev
Pop-Location

$task = 'TecalAlmoxarifadoBFF'
$action = New-ScheduledTaskAction -Execute (Get-Command node).Source -Argument 'dist\server.js' -WorkingDirectory $InstallDir
$trigger = New-ScheduledTaskTrigger -AtStartup
$settings = New-ScheduledTaskSettingsSet -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) -StartWhenAvailable
Unregister-ScheduledTask $task -Confirm:$false -ErrorAction SilentlyContinue
Register-ScheduledTask $task -Action $action -Trigger $trigger -Settings $settings -User 'SYSTEM' -RunLevel Highest | Out-Null

Remove-NetFirewallRule -DisplayName 'Tecal Almoxarifado BFF' -ErrorAction SilentlyContinue
New-NetFirewallRule -DisplayName 'Tecal Almoxarifado BFF' -Direction Inbound -Protocol TCP -LocalPort $Port -RemoteAddress LocalSubnet -Action Allow | Out-Null

Start-ScheduledTask $task
Start-Sleep 5
try { Invoke-RestMethod "http://127.0.0.1:$Port/api/v1/health" | ConvertTo-Json } catch { Write-Warning "Health check falhou: $_" }
