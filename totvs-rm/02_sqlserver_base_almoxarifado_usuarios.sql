/*
 * =====================================================================================
 * SCRIPT 02: CRIAÇÃO DA BASE ALMOXARIFADO, TABELA DE USUÁRIOS E STORED PROCEDURE
 * Servidor: 192.168.7.7 | Base: ALMOXARIFADO
 * Usuário: almoxarife
 * =====================================================================================
 */

-- 1. Cria a base ALMOXARIFADO caso ainda não exista
IF DB_ID(N'ALMOXARIFADO') IS NULL
BEGIN
    CREATE DATABASE [ALMOXARIFADO];
END
GO

USE [ALMOXARIFADO];
GO

-- 2. Mapeia o login [almoxarife] na base ALMOXARIFADO com permissões de leitura/escrita/DDL
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = N'almoxarife')
BEGIN
    EXEC(N'CREATE USER [almoxarife] FOR LOGIN [almoxarife];');
END
GO

ALTER ROLE [db_datareader] ADD MEMBER [almoxarife];
ALTER ROLE [db_datawriter] ADD MEMBER [almoxarife];
ALTER ROLE [db_ddladmin]   ADD MEMBER [almoxarife];
GO

-- 3. Cria a tabela dbo.USUARIOS
IF OBJECT_ID(N'dbo.USUARIOS', N'U') IS NULL
BEGIN
    CREATE TABLE [dbo].[USUARIOS] (
        [ID]            INT IDENTITY(1,1) NOT NULL,
        [LOGIN]         VARCHAR(60)       NOT NULL,
        [NOME_COMPLETO] VARCHAR(120)      NOT NULL,
        [SENHA_HASH]    VARCHAR(64)       NOT NULL,
        [SALT]          VARCHAR(32)       NOT NULL,
        [PERFIL]        VARCHAR(20)       NOT NULL CONSTRAINT [DF_USUARIOS_PERFIL] DEFAULT ('RETIRANTE'),
        [ATIVO]         BIT               NOT NULL CONSTRAINT [DF_USUARIOS_ATIVO] DEFAULT (1),
        [CRIADO_EM]     DATETIME2         NOT NULL CONSTRAINT [DF_USUARIOS_CRIADO_EM] DEFAULT (SYSDATETIME()),
        [ATUALIZADO_EM] DATETIME2         NOT NULL CONSTRAINT [DF_USUARIOS_ATUALIZADO_EM] DEFAULT (SYSDATETIME()),
        CONSTRAINT [PK_USUARIOS] PRIMARY KEY CLUSTERED ([ID] ASC),
        CONSTRAINT [UQ_USUARIOS_LOGIN] UNIQUE NONCLUSTERED ([LOGIN] ASC),
        CONSTRAINT [CK_USUARIOS_PERFIL] CHECK ([PERFIL] IN ('ESTOQUE', 'RETIRANTE', 'AMBOS'))
    );
END
GO

-- 4. Cria ou atualiza a Stored Procedure dbo.SP_SALVAR_USUARIO para facilitar gestão via SSMS
CREATE OR ALTER PROCEDURE [dbo].[SP_SALVAR_USUARIO]
    @LOGIN         VARCHAR(60),
    @NOME_COMPLETO VARCHAR(120),
    @SENHA_TEXTO   NVARCHAR(128) = NULL, -- Se NULL em usuário já existente, mantém a senha atual
    @PERFIL        VARCHAR(20)   = 'RETIRANTE',
    @ATIVO         BIT           = 1
AS
BEGIN
    SET NOCOUNT ON;

    SET @LOGIN = LOWER(LTRIM(RTRIM(@LOGIN)));
    SET @NOME_COMPLETO = LTRIM(RTRIM(@NOME_COMPLETO));
    SET @PERFIL = UPPER(LTRIM(RTRIM(@PERFIL)));

    IF @LOGIN = '' OR @LOGIN IS NULL
    BEGIN
        RAISERROR('O parâmetro @LOGIN é obrigatório.', 16, 1);
        RETURN;
    END

    IF @PERFIL NOT IN ('ESTOQUE', 'RETIRANTE', 'AMBOS')
    BEGIN
        RAISERROR('O parâmetro @PERFIL deve ser ESTOQUE, RETIRANTE ou AMBOS.', 16, 1);
        RETURN;
    END

    DECLARE @SALT VARCHAR(32);
    DECLARE @SENHA_HASH VARCHAR(64);

    IF @SENHA_TEXTO IS NOT NULL AND LEN(@SENHA_TEXTO) > 0
    BEGIN
        -- Gera salt hexadecimal de 32 caracteres (16 bytes)
        SET @SALT = LOWER(CONVERT(VARCHAR(32), CRYPT_GEN_RANDOM(16), 2));
        -- Calcula SHA-256 sobre UTF-16LE (NVARCHAR): idêntico ao Buffer.from(salt + ':' + senha, 'utf16le') do Node.js
        DECLARE @PAYLOAD NVARCHAR(256) = CAST(@SALT AS NVARCHAR(64)) + N':' + @SENHA_TEXTO;
        SET @SENHA_HASH = LOWER(CONVERT(VARCHAR(64), HASHBYTES('SHA2_256', @PAYLOAD), 2));
    END

    IF EXISTS (SELECT 1 FROM [dbo].[USUARIOS] WHERE LOWER([LOGIN]) = @LOGIN)
    BEGIN
        UPDATE [dbo].[USUARIOS]
           SET [NOME_COMPLETO] = @NOME_COMPLETO,
               [SENHA_HASH]    = ISNULL(@SENHA_HASH, [SENHA_HASH]),
               [SALT]          = ISNULL(@SALT, [SALT]),
               [PERFIL]        = @PERFIL,
               [ATIVO]         = @ATIVO,
               [ATUALIZADO_EM] = SYSDATETIME()
         WHERE LOWER([LOGIN]) = @LOGIN;
    END
    ELSE
    BEGIN
        IF @SENHA_HASH IS NULL
        BEGIN
            RAISERROR('Para criar um novo usuário, informe @SENHA_TEXTO.', 16, 1);
            RETURN;
        END

        INSERT INTO [dbo].[USUARIOS] ([LOGIN], [NOME_COMPLETO], [SENHA_HASH], [SALT], [PERFIL], [ATIVO])
        VALUES (@LOGIN, @NOME_COMPLETO, @SENHA_HASH, @SALT, @PERFIL, @ATIVO);
    END
END
GO

-- Concede permissão de execução na procedure ao usuário [almoxarife]
GRANT EXECUTE ON [dbo].[SP_SALVAR_USUARIO] TO [almoxarife];
GO

-- 5. Carga inicial de usuários padrão (Almoxarife + Retirante de teste)
EXEC [dbo].[SP_SALVAR_USUARIO]
    @LOGIN = 'almoxarife',
    @NOME_COMPLETO = 'Almoxarife Tecal (Sessão Tablet)',
    @SENHA_TEXTO = N'Tecal@77',
    @PERFIL = 'AMBOS',
    @ATIVO = 1;
GO

