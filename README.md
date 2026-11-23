# Controle de Retirada do Estoque — Tecal (SQL Server Corpore & ALMOXARIFADO + BFF Node.js + Android Tablet)

Solução corporativa para controle e baixa de retirada física de materiais ao lado da porta do almoxarifado da **Tecal**, integrada diretamente ao **SQL Server (`192.168.7.7`)** nas bases **`Corpore` (TOTVS RM)** e **`ALMOXARIFADO`**.

---

## Fluxo Operacional Exato

1. **Faturamento no Computador (`1.1.13` -> `1.1.10`):** O usuário do estoque fatura o movimento **`1.1.13` (*Requisição de Material ao Almoxarifado - Aprovada*)** para o movimento **`1.1.10` (*Baixa de Estoque*)** apenas com os itens que serão retirados.
2. **Sistema Já Logado na Porta (Fila Pendente):** O tablet Android ao lado da porta permanece logado na sessão do estoque exibindo todos os movimentos **`1.1.10`** ativos que **não possuem os campos complementares `RETIRADO_POR`, `DATA_RETIRADA` e `HORA_RETIRADA` preenchidos** na tabela `Corpore.dbo.TMOVCOMPL`.
3. **Indicação e Conferência 100% pelo Usuário do Estoque:** O estoquista seleciona na tela do tablet o movimento `1.1.10` que está sendo entregue, exibindo **100% dos itens** vinculados (`TITMMOV` + `TPRD`) com barra de progresso de conferência física.
4. **Baixa 100% Total mediante Login e Senha:** Só é permitida a baixa se **100% dos itens do movimento `1.1.10`** forem conferidos. Para efetivar a baixa, o colaborador informa seu **Login e Senha** no modal de assinatura eletrônica.
5. **Gravação Direta na `Corpore.dbo.TMOVCOMPL`:** A API Ponte (`bff-node`) autentica o usuário na tabela `ALMOXARIFADO.dbo.USUARIOS` e grava transacionalmente (`UPDLOCK, ROWLOCK`) os campos **`RETIRADO_POR`**, **`DATA_RETIRADA`** e **`HORA_RETIRADA`** na `Corpore.dbo.TMOVCOMPL`. O movimento sai imediatamente da fila de pendentes e o tablet permanece logado pronto para o próximo atendimento.

---

## Arquitetura do Repositório

```text
.
├── totvs-rm/                                        # Scripts SQL para o SQL Server (192.168.7.7)
│   ├── 01_sqlserver_permissoes_corpore.sql          # GRANT SELECT em TMOV/TITMMOV/TPRD/GCCUSTO/TMOVRELAC e SELECT/INSERT/UPDATE em TMOVCOMPL
│   └── 02_sqlserver_base_almoxarifado_usuarios.sql  # Criação da base ALMOXARIFADO, tabela USUARIOS e procedure SP_SALVAR_USUARIO
│
├── bff-node/                                        # API Ponte em Node.js / TypeScript + Fastify + mssql
│   ├── .env & .env.example                          # Configuração do SQL Server (192.168.7.7:1433), Coligada e colunas TMOVCOMPL
│   └── src/                                         # Pool SQL Server, autenticação SHA-256 + Salt, rotas REST e testes automatizados
│
└── android-app/                                     # App Android Tablet Kiosk (Kotlin + Jetpack Compose Material 3)
    └── app/src/main/                                # Dashboard Industrial Split-Screen, Progresso 100%, Gestão de Usuários e Modo Quiosque
```

---

## Guia Rápido de Execução

### 1. Preparar o Banco de Dados SQL Server (`192.168.7.7`)
Execute no SQL Server Management Studio (SSMS):
1. [`totvs-rm/01_sqlserver_permissoes_corpore.sql`](./totvs-rm/01_sqlserver_permissoes_corpore.sql)
2. [`totvs-rm/02_sqlserver_base_almoxarifado_usuarios.sql`](./totvs-rm/02_sqlserver_base_almoxarifado_usuarios.sql)

### 2. Executar a API Ponte (`bff-node`)
```powershell
cd bff-node
npm install
npm run dev
```

### 3. Executar o Aplicativo Android (`android-app`)
1. Abra a pasta `android-app` no **Android Studio** e execute no Tablet Android.
2. No botão **Configurar Terminal / Usuários / TI** (protegido pelo PIN Administrativo padrão `1110`), aponte a **URL da API Ponte (`bff-node`)** e gerencie os usuários da base `ALMOXARIFADO` diretamente pela aba **Usuários (Base ALMOXARIFADO)**.
