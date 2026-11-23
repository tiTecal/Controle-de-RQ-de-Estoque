/*
 * =====================================================================================
 * SCRIPT 01: CRIAÇÃO DAS COLUNAS NA TMOVCOMPL E PERMISSÕES NA BASE CORPORE (TOTVS RM)
 * Servidor: 192.168.7.7 | Base: Corpore
 * Usuário: almoxarife
 * =====================================================================================
 */

USE [Corpore];
GO

-- 1. Garante que o usuário de banco exista mapeado para o login [almoxarife]
-- (Usamos EXEC dinâmico para evitar o sublinhado vermelho falso do IntelliSense do SSMS quando o usuário já existe)
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'almoxarife')
BEGIN
    EXEC(N'CREATE USER [almoxarife] FOR LOGIN [almoxarife];');
END
GO

-- 2. Cria as 3 colunas complementares na dbo.TMOVCOMPL caso ainda não existam
IF COL_LENGTH('dbo.TMOVCOMPL', 'RETIRADO_POR') IS NULL
BEGIN
    ALTER TABLE [dbo].[TMOVCOMPL] ADD [RETIRADO_POR] VARCHAR(60) NULL;
    PRINT 'Coluna [RETIRADO_POR] criada com sucesso na dbo.TMOVCOMPL.';
END
ELSE
BEGIN
    PRINT 'Coluna [RETIRADO_POR] já existe na dbo.TMOVCOMPL.';
END
GO

IF COL_LENGTH('dbo.TMOVCOMPL', 'DATA_RETIRADA') IS NULL
BEGIN
    ALTER TABLE [dbo].[TMOVCOMPL] ADD [DATA_RETIRADA] DATETIME NULL;
    PRINT 'Coluna [DATA_RETIRADA] criada com sucesso na dbo.TMOVCOMPL.';
END
ELSE
BEGIN
    PRINT 'Coluna [DATA_RETIRADA] já existe na dbo.TMOVCOMPL.';
END
GO

IF COL_LENGTH('dbo.TMOVCOMPL', 'HORA_RETIRADA') IS NULL
BEGIN
    ALTER TABLE [dbo].[TMOVCOMPL] ADD [HORA_RETIRADA] VARCHAR(8) NULL;
    PRINT 'Coluna [HORA_RETIRADA] criada com sucesso na dbo.TMOVCOMPL.';
END
ELSE
BEGIN
    PRINT 'Coluna [HORA_RETIRADA] já existe na dbo.TMOVCOMPL.';
END
GO

-- 3. Permissão de leitura nas tabelas do movimento 1.1.10 e seus itens
GRANT SELECT ON [dbo].[TMOV]      TO [almoxarife];
GRANT SELECT ON [dbo].[TITMMOV]   TO [almoxarife];
GRANT SELECT ON [dbo].[TPRD]      TO [almoxarife];
GRANT SELECT ON [dbo].[GCCUSTO]   TO [almoxarife];
GRANT SELECT ON [dbo].[TMOVRELAC] TO [almoxarife];

-- 4. Permissão de leitura nas tabelas de Funcionários, Seções, Funções e Usuários do RM
--    (Usado para buscar APENAS os funcionários que trabalham no ESTOQUE / ALMOXARIFADO na Coligada 2)
IF OBJECT_ID('dbo.PFUNC', 'U') IS NOT NULL      GRANT SELECT ON [dbo].[PFUNC]      TO [almoxarife];
IF OBJECT_ID('dbo.PSECAO', 'U') IS NOT NULL     GRANT SELECT ON [dbo].[PSECAO]     TO [almoxarife];
IF OBJECT_ID('dbo.PFUNCAO', 'U') IS NOT NULL    GRANT SELECT ON [dbo].[PFUNCAO]    TO [almoxarife];
IF OBJECT_ID('dbo.PPESSOA', 'U') IS NOT NULL    GRANT SELECT ON [dbo].[PPESSOA]    TO [almoxarife];
IF OBJECT_ID('dbo.GUSUARIO', 'U') IS NOT NULL   GRANT SELECT ON [dbo].[GUSUARIO]   TO [almoxarife];
IF OBJECT_ID('dbo.GUSRPERFIL', 'U') IS NOT NULL GRANT SELECT ON [dbo].[GUSRPERFIL] TO [almoxarife];
IF OBJECT_ID('dbo.TUSRTMV', 'U') IS NOT NULL    GRANT SELECT ON [dbo].[TUSRTMV]    TO [almoxarife];
GO

-- 5. Permissão de leitura, inserção e atualização EXCLUSIVAMENTE na tabela de campos complementares
GRANT SELECT, INSERT, UPDATE ON [dbo].[TMOVCOMPL] TO [almoxarife];
GO

PRINT 'SUCESSO: Permissões aplicadas (incluindo leitura de funcionários do Estoque) na base Corpore!';
GO
