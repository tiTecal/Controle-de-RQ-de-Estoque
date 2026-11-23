# Implantação do BFF Almoxarifado — servidor 192.168.7.3 (Git + NSSM)

## 1. Primeira instalação no servidor 192.168.7.3
Abra o PowerShell **como Administrador** no servidor (com Git, Node.js 22+ e NSSM instalados):

```powershell
mkdir C:\Tecal -Force
cd C:\Tecal
git clone <URL_DO_REPOSITORIO> almoxarifado-bff
cd C:\Tecal\almoxarifado-bff\bff-node\deploy
.\instalar-nssm.ps1
```

- Na primeira execução, o script cria `bff-node\.env` a partir do template, gera um `BFF_JWT_SECRET` aleatório e abre o Bloco de Notas para preencher `DB_PASSWORD` e `BFF_ADMIN_PIN_SHA256`.
- Salve o `.env` e rode `.\instalar-nssm.ps1` novamente.
- O script instala as dependências, compila (`dist\server.js`), registra o serviço `TecalAlmoxarifadoBFF` no NSSM com rotação de logs em `bff-node\logs\`, libera a porta `8080` apenas para a sub-rede local e valida `http://127.0.0.1:8080/api/v1/health`.

> Se o `nssm.exe` não estiver no PATH, informe o caminho:
> `.\instalar-nssm.ps1 -NssmPath "C:\nssm\win64\nssm.exe"`

## 2. Atualizações futuras no servidor
```powershell
cd C:\Tecal\almoxarifado-bff\bff-node\deploy
.\atualizar-nssm.ps1
```

## 3. Pré-requisitos de rede
- `192.168.7.3` → `192.168.7.7:1433` (SQL Server) e `192.168.7.7:8051` (RM Host).
- Tablets → `192.168.7.3:8080`.

## 4. App Android
O app tem `http://192.168.9.230:8080/` como URL padrão (`SessionPreferences.DEFAULT_BFF_URL`).
Após validar o serviço em `http://192.168.7.3:8080/api/v1/health`, troque a URL padrão para `http://192.168.7.3:8080/` (ou altere na tela administrativa do app).
