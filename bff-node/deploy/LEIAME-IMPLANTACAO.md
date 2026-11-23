# Implantação do BFF Almoxarifado — servidor 192.168.7.3 (Git + NSSM)

Segue o *Manual de Transferência de Aplicações para Produção e Backup* (TI Tecal).

| Item | Valor |
|---|---|
| Pasta em produção | `C:\projetos\Controle-de-RQ-de-Estoque` (API em `bff-node\`) |
| Serviço NSSM | `Projeto de TI - Controle de RQ de Estoque-api` |
| Executável / argumentos | `node.exe` / `dist\server.js` (diretório de trabalho `bff-node`) |
| Porta | `7777` (firewall liberado só para a sub-rede local) |
| Logs | `C:\projetos\00 - logs\Controle de RQ de Estoque\api.log` e `api.error.log` (rotação em 10 MB) |
| Backup do setor | `\\192.168.7.5\TI\Projetos TI\Controle-de-RQ-de-Estoque` (+ `.zip` datado) |

## 1. Primeira instalação no servidor 192.168.7.3
PowerShell **como Administrador** (Git, Node.js 22+ e NSSM instalados):

```powershell
cd C:\projetos
git clone <URL_DO_REPOSITORIO> Controle-de-RQ-de-Estoque
cd C:\projetos\Controle-de-RQ-de-Estoque\bff-node

# (opcional, recomendado pelo manual) teste manual antes de virar serviço
npm ci
npm run build
node dist\server.js      # validar http://127.0.0.1:7777/api/v1/health e encerrar com Ctrl+C

cd deploy
.\instalar-nssm.ps1 -NssmPath "C:\nssm-2.24\win64\nssm.exe"
```

- Na primeira execução o script cria `bff-node\.env` a partir do template, gera um `BFF_JWT_SECRET` aleatório e abre o Bloco de Notas para preencher `DB_PASSWORD` (e `BFF_ADMIN_PIN_SHA256`, se mudar o PIN). Salve e rode o script novamente.
- Para instalar o serviço antes de ter a senha do banco: `.\instalar-nssm.ps1 -AllowPendingDbPassword`. Depois de preencher o `.env`, reinicie: `nssm restart "Projeto de TI - Controle de RQ de Estoque-api"`.
- O script instala dependências (`npm ci`), compila, restringe o `.env` a SYSTEM/Administradores, registra o serviço (início automático, reinício em falha, parada limpa de 15 s), libera a porta e valida `http://127.0.0.1:7777/api/v1/health`.
- Serviço legado `TecalAlmoxarifadoBFF` (nome antigo) é removido automaticamente, se existir.

## 2. Atualizações futuras
```powershell
cd C:\projetos\Controle-de-RQ-de-Estoque\bff-node\deploy
.\atualizar-nssm.ps1 -NssmPath "C:\nssm-2.24\win64\nssm.exe"
```
Ordem executada (manual, seção 10): parar serviço → `git pull` → `npm ci` → `npm run build` → iniciar → health check.

## 3. Comandos úteis
```powershell
nssm status  "Projeto de TI - Controle de RQ de Estoque-api"
nssm restart "Projeto de TI - Controle de RQ de Estoque-api"
nssm stop    "Projeto de TI - Controle de RQ de Estoque-api"
```

## 4. Pré-requisitos de rede
- `192.168.7.3` → `192.168.7.7:1433` (SQL Server) e `192.168.7.7:8051` (RM Host).
- Tablets → `192.168.7.3:7777`.

## 5. App Android
A URL padrão do app é `http://192.168.7.3:7777/` (`SessionPreferences.DEFAULT_BFF_URL`). Em tablets já instalados, altere em *Painel Administrativo → Servidor & Quiosque* (PIN administrativo).
