# API Ponte (BFF Node.js/Fastify + SQL Server) — Controle de Retirada do Estoque Tecal

API Ponte corporativa em **Node.js (TypeScript + Fastify + mssql)** que integra o aplicativo **Android Tablet Kiosk** ao lado da porta do almoxarifado diretamente com o **SQL Server (`192.168.7.7`)** nas bases **`Corpore` (TOTVS RM)** e **`ALMOXARIFADO`**.

---

## Recursos & Funcionalidades

- **100% Compatível com Contratos Android:** Mantém exatamente os mesmos endpoints, corpos de requisição/resposta e cabeçalhos JWT.
- **Conexão Direta ao SQL Server (`192.168.7.7:1433`):**
  - **Base `Corpore`:** Consultas parametrizadas (`SELECT`) em `TMOV`, `TITMMOV`, `TPRD`, `GCCUSTO`, `TMOVRELAC` e baixa transacional (`SELECT`, `INSERT`, `UPDATE` com `UPDLOCK, ROWLOCK, HOLDLOCK`) exclusivamente nos campos complementares `RETIRADO_POR`, `DATA_RETIRADA` e `HORA_RETIRADA` da tabela `TMOVCOMPL`.
  - **Base `ALMOXARIFADO`:** Tabela `dbo.USUARIOS` com perfis (`ESTOQUE`, `RETIRANTE`, `AMBOS`), hash `SHA-256 + Salt` compatível com `HASHBYTES` do SQL Server e Stored Procedure `dbo.SP_SALVAR_USUARIO` para manutenção rápida via SSMS.
- **Semântica Específica de Erros:**
  - `HTTP 403` (`INVALID_WITHDRAWER_CREDENTIALS`): Erro de credenciais no modal de confirmação (não derruba a sessão do tablet).
  - `HTTP 401` (`SESSION_EXPIRED` / `INVALID_ESTOQUE_CREDENTIALS`): Token expirado ou login inicial incorreto.
  - `HTTP 409` (`ALREADY_WITHDRAWN`): Movimento 1.1.10 já baixado anteriormente.
  - `HTTP 502` (`RM_INTEGRATION_ERROR`): Falha de comunicação com o SQL Server.
- **Modo Simulador em Memória (`RM_MOCK_MODE=true`):**
  - Permite homologar o aplicativo Android sem necessitar de conexão física com o SQL Server.
  - Credenciais de teste: `tecal123` ou `rm123`.
- **Concorrência Segura:** Lock com Mutex por `idMov` em memória somado a `UPDLOCK, ROWLOCK` no SQL Server.
- **Idempotência:** Cache de requisição com chave única (`idempotencyKey`).

---

## Endpoints de Gestão de Usuários (`/api/v1/usuarios`)

Protegidos pelo token `Bearer <JWT>` da sessão do estoque:
- `GET /api/v1/usuarios`: Lista todos os usuários da base `ALMOXARIFADO`.
- `POST /api/v1/usuarios`: Cria ou atualiza um usuário (`login`, `nomeCompleto`, `senha`, `perfil`, `ativo`).
- `PATCH /api/v1/usuarios/:login/status`: Ativa ou inativa um usuário (`{ "ativo": false }`).

---

## Execução Local

### 1. Instalar Dependências
```powershell
cd bff-node
npm install
```

### 2. Executar em Modo de Desenvolvimento
```powershell
npm run dev
```
O servidor iniciará em `http://0.0.0.0:8080`.

### 3. Executar Testes Automatizados
```powershell
npm test
```

### 4. Build de Produção
```powershell
npm run build
npm start
```
