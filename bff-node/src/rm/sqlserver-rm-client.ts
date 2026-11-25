import sql from 'mssql';
import { AppConfig } from '../config/env.js';
import {
  BusinessValidationError,
  MovementAlreadyWithdrawnError,
  MovementNotFoundError,
  TotvsRmIntegrationError
} from '../domain/errors.js';
import {
  AlmoxarifadoUserDto,
  MovementItemDto,
  PendingMovementDto,
  UpsertUserRequest,
  UserProfileRole
} from '../domain/types.js';
import { verifyTotvsRmGusuarioPassword } from './bcrypt-verifier.js';
import { PasswordHasher } from './password-hasher.js';
import {
  resolveRmHttpBaseUrl,
  RmHostAuthClient
} from './rm-auth-helpers.js';
import { TotvsRmGateway } from './totvs-rm-gateway.interface.js';
import {
  buildSafeNameSlug,
  createOpaqueRmRef,
  isCpfNumericLogin,
  maskCpfLgpd,
  onlyDigits
} from '../security/security.js';

function sanitizeSqlIdentifier(identifier: string, label: string): string {
  const clean = identifier.trim();
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(clean)) {
    throw new Error(`Identificador SQL inválido para ${label}: '${identifier}'`);
  }
  return clean;
}

const MOVEMENTS_CACHE_TTL_MS = 20_000; // 20 segundos
const ITEMS_CACHE_TTL_MS = 10 * 60_000; // 10 minutos
const USERS_CACHE_TTL_MS = 30 * 60_000; // 30 minutos

interface InternalUserRecord {
  ref: string;
  realRmLogin: string;
  candidateLogins: string[];
  fullCpf: string;
  chapa: string;
  safeLabel: string;
  publicDto: AlmoxarifadoUserDto;
}

export class SqlServerRmClient implements TotvsRmGateway {
  private poolPromise: Promise<sql.ConnectionPool> | null = null;
  private bootstrapDone = false;

  // Caches em memória para eliminar tráfego repetitivo e manter PII sensível isolada no BFF
  private movementsCache: { data: PendingMovementDto[]; expiresAt: number } | null = null;
  private itemsCache = new Map<number, { data: MovementItemDto[]; expiresAt: number }>();
  private usersCache: { data: AlmoxarifadoUserDto[]; expiresAt: number } | null = null;
  private internalUsersByRef = new Map<string, InternalUserRecord>();
  private internalUsersByCpf = new Map<string, InternalUserRecord>();

  private readonly corporeDb: string;
  private readonly almoxDb: string;
  private readonly colRetiradoPor: string;
  private readonly colDataRetirada: string;
  private readonly colHoraRetirada: string;
  private readonly rmHostAuthClient: RmHostAuthClient;

  constructor(private readonly config: AppConfig) {
    this.corporeDb = sanitizeSqlIdentifier(config.dbCorporeName, 'DB_CORPORE_NAME');
    this.almoxDb = sanitizeSqlIdentifier(config.dbAlmoxName, 'DB_ALMOX_NAME');
    this.colRetiradoPor = sanitizeSqlIdentifier(config.colRetiradoPor, 'RM_COL_RETIRADO_POR');
    this.colDataRetirada = sanitizeSqlIdentifier(config.colDataRetirada, 'RM_COL_DATA_RETIRADA');
    this.colHoraRetirada = sanitizeSqlIdentifier(config.colHoraRetirada, 'RM_COL_HORA_RETIRADA');
    this.rmHostAuthClient = new RmHostAuthClient(
      resolveRmHttpBaseUrl(config.rmBaseUrl, config.dbHost)
    );
  }

  private async getPool(): Promise<sql.ConnectionPool> {
    if (!this.poolPromise) {
      const sqlConfig: sql.config = {
        server: this.config.dbHost,
        port: this.config.dbPort,
        user: this.config.dbUser,
        password: this.config.dbPassword,
        database: this.corporeDb,
        connectionTimeout: 15_000,
        requestTimeout: 20_000,
        pool: {
          max: 5,
          min: 1,
          idleTimeoutMillis: 300_000
        },
        options: {
          encrypt: this.config.dbEncrypt,
          trustServerCertificate: this.config.dbTrustServerCertificate
        }
      };

      const pool = new sql.ConnectionPool(sqlConfig);
      this.poolPromise = pool.connect().catch((err: unknown) => {
        this.poolPromise = null;
        throw new TotvsRmIntegrationError(
          'Falha temporária de comunicação com o servidor corporativo do ERP.',
          err
        );
      });
    }

    const pool = await this.poolPromise;
    if (!this.bootstrapDone) {
      await this.ensureAlmoxarifadoBootstrap(pool);
      this.bootstrapDone = true;
    }
    return pool;
  }

  private async ensureAlmoxarifadoBootstrap(pool: sql.ConnectionPool): Promise<void> {
    try {
      await pool.request().query(`
        IF OBJECT_ID(N'[${this.almoxDb}].[dbo].[USUARIOS]', N'U') IS NULL
        BEGIN
          CREATE TABLE [${this.almoxDb}].[dbo].[USUARIOS] (
            [ID]            INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
            [LOGIN]         VARCHAR(60)       NOT NULL UNIQUE,
            [NOME_COMPLETO] VARCHAR(120)      NOT NULL,
            [SENHA_HASH]    VARCHAR(64)       NOT NULL,
            [SALT]          VARCHAR(32)       NOT NULL,
            [PERFIL]        VARCHAR(20)       NOT NULL DEFAULT ('RETIRANTE'),
            [ATIVO]         BIT               NOT NULL DEFAULT (1),
            [CRIADO_EM]     DATETIME2         NOT NULL DEFAULT (SYSDATETIME()),
            [ATUALIZADO_EM] DATETIME2         NOT NULL DEFAULT (SYSDATETIME())
          );
        END
      `);

      const countRes = await pool
        .request()
        .query(`SELECT COUNT(1) AS TOTAL FROM [${this.almoxDb}].[dbo].[USUARIOS] WITH (NOLOCK);`);

      const total = Number(countRes.recordset?.[0]?.TOTAL ?? 0);
      if (total === 0 && this.config.dbUser && this.config.dbPassword) {
        const defaultLogin = this.config.dbUser.trim().toLowerCase();
        const salt = PasswordHasher.generateSalt();
        const senhaHash = PasswordHasher.hashPassword(this.config.dbPassword, salt);

        await pool
          .request()
          .input('login', sql.VarChar(60), defaultLogin)
          .input('nomeCompleto', sql.VarChar(120), 'Almoxarife Tecal (Sessão Tablet)')
          .input('senhaHash', sql.VarChar(64), senhaHash)
          .input('salt', sql.VarChar(32), salt)
          .input('perfil', sql.VarChar(20), 'AMBOS').query(`
            INSERT INTO [${this.almoxDb}].[dbo].[USUARIOS]
              ([LOGIN], [NOME_COMPLETO], [SENHA_HASH], [SALT], [PERFIL], [ATIVO])
            VALUES
              (@login, @nomeCompleto, @senhaHash, @salt, @perfil, 1);
          `);
      }
    } catch {
      // Ignora caso não haja permissão DDL
    }
  }

  private sanitizePersonField(rawValue: string): string {
    const clean = (rawValue || '').trim();
    if (!clean) return '';
    if (isCpfNumericLogin(clean)) {
      const digits = onlyDigits(clean);
      const cached = this.internalUsersByCpf.get(digits);
      if (cached?.safeLabel) {
        return cached.safeLabel;
      }
      return maskCpfLgpd(digits);
    }
    return clean;
  }

  private async ensureUsersLoaded(): Promise<void> {
    if (!this.usersCache || Date.now() >= this.usersCache.expiresAt) {
      await this.listUsers();
    }
  }

  async resolveSafeWithdrawerLabel(loginOrRef: string): Promise<string> {
    const clean = (loginOrRef || '').trim();
    if (!clean) return '';
    if (clean.toLowerCase().startsWith('rm_')) {
      await this.ensureUsersLoaded();
      const internal = this.internalUsersByRef.get(clean.toLowerCase());
      if (internal) {
        return internal.safeLabel;
      }
    }
    return this.sanitizePersonField(clean);
  }

  async authenticateRmUser(
    login: string,
    senha: string,
    requiredContext?: 'ESTOQUE' | 'RETIRANTE'
  ): Promise<boolean> {
    const cleanInput = login.trim().toLowerCase();
    if (!cleanInput || !senha) return false;

    try {
      const pool = await this.getPool();

      // Se recebeu uma referência opaca 'rm_...', resolve internamente na memória do BFF
      let internalEntry = this.internalUsersByRef.get(cleanInput);
      if (!internalEntry && cleanInput.startsWith('rm_')) {
        await this.ensureUsersLoaded();
        internalEntry = this.internalUsersByRef.get(cleanInput);
      }

      const effectiveLogin = (internalEntry?.realRmLogin || cleanInput).trim().toLowerCase();
      const cleanCpfDigits = internalEntry?.fullCpf || onlyDigits(effectiveLogin);

      // 1. Verifica na tabela auxiliar [ALMOXARIFADO].[dbo].[USUARIOS] (apenas contas locais)
      if (!internalEntry || !internalEntry.publicDto.origemRm) {
        try {
          const result = await pool
            .request()
            .input('login', sql.VarChar(60), effectiveLogin)
            .query(`
              SELECT TOP 1
                [LOGIN],
                [SENHA_HASH],
                [SALT],
                UPPER(LTRIM(RTRIM([PERFIL]))) AS [PERFIL],
                [ATIVO]
              FROM [${this.almoxDb}].[dbo].[USUARIOS] WITH (NOLOCK)
              WHERE LOWER(LTRIM(RTRIM([LOGIN]))) = @login;
            `);

          const userRow = result.recordset?.[0];
          if (userRow && Boolean(userRow.ATIVO)) {
            const perfil = String(userRow.PERFIL || 'RETIRANTE').toUpperCase();
            const roleAllowed =
              requiredContext === 'ESTOQUE'
                ? perfil === 'ESTOQUE' || perfil === 'AMBOS'
                : true;

            if (roleAllowed) {
              const validLocal = PasswordHasher.verifyPassword(
                senha,
                String(userRow.SALT || ''),
                String(userRow.SENHA_HASH || '')
              );
              if (validLocal) {
                return true;
              }
            }
          }
        } catch {
          // Segue para validação direta no TOTVS RM
        }
      }

      // Antes de autenticar na GUSUARIO/RM Host, valida se o contexto exige perfil ESTOQUE
      if (requiredContext === 'ESTOQUE') {
        await this.ensureUsersLoaded();
        const rmEntry =
          internalEntry ||
          (cleanCpfDigits.length === 11 ? this.internalUsersByCpf.get(cleanCpfDigits) : undefined);
        if (
          rmEntry &&
          rmEntry.publicDto.perfil !== 'ESTOQUE' &&
          rmEntry.publicDto.perfil !== 'AMBOS'
        ) {
          return false;
        }
      }

      // 2. Validação direta na Corpore.dbo.GUSUARIO EXCLUSIVAMENTE pelo CPF (11 dígitos)
      // Nunca busca nem autentica por CODUSUARIO nomeado (ex.: raphael.araujo) ou 2-CHAPA.
      const candidateLogins: string[] = cleanCpfDigits.length === 11 ? [cleanCpfDigits] : [];
      const activeAccountPriority: Array<{ codUsuario: string; isLegacyNonBcrypt: boolean }> = [];
      let gusuarioQuerySucceeded = false;

      if (candidateLogins.length > 0) {
        try {
          const req = pool.request();
          req.input('cpf', sql.VarChar(60), cleanCpfDigits);

          const rmResult = await req.query(`
            SELECT
              LTRIM(RTRIM(U.CODUSUARIO)) AS CODUSUARIO,
              U.SENHA
            FROM [${this.corporeDb}].[dbo].[GUSUARIO] U WITH (NOLOCK)
            WHERE ISNULL(U.STATUS, 1) = 1
              AND LTRIM(RTRIM(U.CODUSUARIO)) = @cpf
            ORDER BY COALESCE(U.DATAULTIMOACESSOVALIDO, U.DATAULTIMOACESSO, U.RECCREATEDON) DESC;
          `);
          gusuarioQuerySucceeded = true;

          // 2a. Verifica offline todos os hashes BCrypt primeiro (zero risco de incremento de NUMLOGININVALIDO no RM)
          const seenCodUsuario = new Set<string>();
          for (const rmRow of rmResult.recordset || []) {
            const codUsuario = String(rmRow.CODUSUARIO || '').trim();
            const rmHash = String(rmRow.SENHA || '').trim();
            if (rmHash && verifyTotvsRmGusuarioPassword(senha, rmHash)) {
              return true;
            }
            if (codUsuario && !seenCodUsuario.has(codUsuario.toLowerCase())) {
              seenCodUsuario.add(codUsuario.toLowerCase());
              const isLegacyNonBcrypt = !rmHash || (!rmHash.includes('$2') && !rmHash.startsWith('#'));
              activeAccountPriority.push({ codUsuario, isLegacyNonBcrypt });
            }
          }
        } catch {
          // Segue para fallback no servidor TOTVS RM Host caso GUSUARIO não esteja acessível
        }
      }

      // 3. Fallback oficial no servidor TOTVS RM Host (/api/connect/token + SOAP IwsBase) apenas por CPF (11 dígitos)
      let remoteTargets: string[];
      if (activeAccountPriority.length > 0) {
        const legacyFirst = activeAccountPriority.filter((acc) => acc.isLegacyNonBcrypt);
        const bcryptRest = activeAccountPriority.filter((acc) => !acc.isLegacyNonBcrypt);
        remoteTargets = [...legacyFirst, ...bcryptRest].map((acc) => acc.codUsuario).slice(0, 1);
      } else if (!gusuarioQuerySucceeded && cleanCpfDigits.length === 11) {
        remoteTargets = [cleanCpfDigits];
      } else {
        remoteTargets = [];
      }

      for (const targetLogin of remoteTargets) {
        const remoteOk = await this.rmHostAuthClient.verifyCredential(targetLogin, senha);
        if (remoteOk) {
          return true;
        }
      }

      return false;
    } catch (err: unknown) {
      if (err instanceof TotvsRmIntegrationError) throw err;
      throw new TotvsRmIntegrationError(
        'Erro ao validar credenciais do colaborador no ERP.',
        err
      );
    }
  }

  async listPending1110Movements(_codColigada: number): Promise<PendingMovementDto[]> {
    // Retorna do cache se ainda estiver válido (reduz drasticamente o consumo de rede no 192.168.7.7)
    if (this.movementsCache && Date.now() < this.movementsCache.expiresAt) {
      return this.movementsCache.data;
    }

    // Fixado estritamente na Coligada 2 (Tecal Engenharia)
    const targetColigada = _codColigada > 0 ? _codColigada : (this.config.rmCodColigada || 2);
    const minDataEmissao = this.config.rmMinDataEmissao.trim() || null;

    const query = `
      SELECT TOP 100
          M.CODCOLIGADA,
          M.IDMOV,
          ISNULL(M.NUMEROMOV, CAST(M.IDMOV AS VARCHAR(20))) AS NUMEROMOV,
          M.CODTMV,
          M.CODFILIAL,
          ISNULL(M.CODLOC, '') AS CODLOC,
          CASE
              WHEN ISNULL(LTRIM(RTRIM(M.CODLOC)), '') <> '' THEN 'Local ' + LTRIM(RTRIM(M.CODLOC))
              ELSE 'Almoxarifado Tecal Engenharia'
          END AS NOMELOCAL,
          CONVERT(VARCHAR(19), M.DATAEMISSAO, 126) AS DATAEMISSAO,
          ISNULL(M.CODUSUARIO, '') AS USUARIOESTOQUISTA,
          ISNULL(M.CODCCUSTO, '') AS CODCCUSTO,
          ISNULL(CC.NOME, '') AS NOMECCUSTO,
          ISNULL(ORIG.NUMEROMOV_ORIGEM, '') AS NUMEROREQUISICAO1113,
          ISNULL(NULLIF(LTRIM(RTRIM(ORIG.SOLICITANTE_ORIGEM)), ''), ISNULL(M.CODUSUARIO, '')) AS SOLICITANTE,
          ISNULL(ITENS_AGG.QTD_ITENS, 0) AS QTDITENS,
          ISNULL(LTRIM(RTRIM(M.STATUS)), 'A') AS STATUS_RM,
          ISNULL(LTRIM(RTRIM(CAST(MC.[${this.colRetiradoPor}] AS VARCHAR(60)))), '') AS RETIRADO_POR,
          ISNULL(LTRIM(RTRIM(CONVERT(VARCHAR(10), MC.[${this.colDataRetirada}], 120))), '') AS DATA_RETIRADA,
          ISNULL(LTRIM(RTRIM(CAST(MC.[${this.colHoraRetirada}] AS VARCHAR(8)))), '') AS HORA_RETIRADA
      FROM [${this.corporeDb}].[dbo].[TMOV] M WITH (NOLOCK)
      LEFT JOIN [${this.corporeDb}].[dbo].[TMOVCOMPL] MC WITH (NOLOCK)
          ON MC.CODCOLIGADA = M.CODCOLIGADA
         AND MC.IDMOV = M.IDMOV
      LEFT JOIN [${this.corporeDb}].[dbo].[GCCUSTO] CC WITH (NOLOCK)
          ON CC.CODCOLIGADA = M.CODCOLIGADA
         AND CC.CODCCUSTO = M.CODCCUSTO
      OUTER APPLY (
          SELECT TOP 1
              ISNULL(MORIG.NUMEROMOV, CAST(MORIG.IDMOV AS VARCHAR(20))) AS NUMEROMOV_ORIGEM,
              ISNULL(MORIG.CODUSUARIO, '') AS SOLICITANTE_ORIGEM
          FROM [${this.corporeDb}].[dbo].[TMOVRELAC] REL WITH (NOLOCK)
          INNER JOIN [${this.corporeDb}].[dbo].[TMOV] MORIG WITH (NOLOCK)
              ON MORIG.CODCOLIGADA = REL.CODCOLORIGEM
             AND MORIG.IDMOV = REL.IDMOVORIGEM
          WHERE REL.CODCOLDESTINO = M.CODCOLIGADA
            AND REL.IDMOVDESTINO = M.IDMOV
          ORDER BY MORIG.IDMOV DESC
      ) ORIG
      OUTER APPLY (
          SELECT COUNT(1) AS QTD_ITENS
          FROM [${this.corporeDb}].[dbo].[TITMMOV] I WITH (NOLOCK)
          WHERE I.CODCOLIGADA = M.CODCOLIGADA
            AND I.IDMOV = M.IDMOV
      ) ITENS_AGG
      WHERE M.CODCOLIGADA = @codColigada
        AND M.CODTMV = '1.1.10'
        AND ISNULL(M.STATUS, '') <> 'C'
        AND (@minDataEmissao IS NULL OR M.DATAEMISSAO >= CONVERT(DATETIME, @minDataEmissao, 120))
      ORDER BY M.DATAEMISSAO DESC, M.IDMOV DESC;
    `;

    try {
      const pool = await this.getPool();
      const result = await pool
        .request()
        .input('codColigada', sql.SmallInt, targetColigada)
        .input('minDataEmissao', sql.VarChar(30), minDataEmissao)
        .query(query);

      // Garante mapa interno carregado para sanitizar eventuais CPFs em campos de usuário do RM
      await this.ensureUsersLoaded();

      const rows = result.recordset || [];
      const mapped = rows.map((row) => {
        const codTmv = String(row.CODTMV || '1.1.10').trim();
        const statusRm = String(row.STATUS_RM || 'A').trim().toUpperCase();
        const retiradoPor = this.sanitizePersonField(String(row.RETIRADO_POR || '').trim());
        const dataRetirada = String(row.DATA_RETIRADA || '').trim();
        const horaRetirada = String(row.HORA_RETIRADA || '').trim();
        const jaRetirado = Boolean(retiradoPor) || statusRm === 'F';
        const rawNomeCCusto = String(row.NOMECCUSTO || '').trim();

        return {
          codColigada: targetColigada,
          idMov: Number(row.IDMOV),
          numeroMov: String(row.NUMEROMOV || row.IDMOV).trim(),
          codTmv,
          codFilial: Number(row.CODFILIAL) || 1,
          codLoc: String(row.CODLOC || '').trim(),
          nomeLocal: String(row.NOMELOCAL || 'Almoxarifado Principal').trim(),
          dataEmissao: String(row.DATAEMISSAO || '').trim(),
          usuarioEstoquista: this.sanitizePersonField(String(row.USUARIOESTOQUISTA || '').trim()),
          codCCusto: String(row.CODCCUSTO || '').trim(),
          nomeCCusto: rawNomeCCusto,
          numeroRequisicao1113: String(row.NUMEROREQUISICAO1113 || '').trim(),
          solicitante: this.sanitizePersonField(String(row.SOLICITANTE || '').trim()),
          qtdItens: Number(row.QTDITENS) || 0,
          statusRm,
          jaRetirado,
          retiradoPor,
          dataRetirada,
          horaRetirada
        };
      });

      this.movementsCache = {
        data: mapped,
        expiresAt: Date.now() + MOVEMENTS_CACHE_TTL_MS
      };

      return mapped;
    } catch (err: unknown) {
      if (err instanceof TotvsRmIntegrationError) throw err;
      throw new TotvsRmIntegrationError(
        'Erro ao listar movimentações pendentes no ERP.',
        err
      );
    }
  }

  async listMovement1110Items(_codColigada: number, idMov: number): Promise<MovementItemDto[]> {
    const cached = this.itemsCache.get(idMov);
    if (cached && Date.now() < cached.expiresAt) {
      return cached.data;
    }

    // Fixado estritamente na Coligada 2 (Tecal Engenharia)
    const targetColigada = _codColigada > 0 ? _codColigada : (this.config.rmCodColigada || 2);
    try {
      const pool = await this.getPool();

      const headerCheck = await pool
        .request()
        .input('codColigada', sql.SmallInt, targetColigada)
        .input('idMov', sql.Int, idMov)
        .query(`
          SELECT TOP 1 CODCOLIGADA, IDMOV
          FROM [${this.corporeDb}].[dbo].[TMOV] WITH (NOLOCK)
          WHERE CODCOLIGADA = @codColigada
            AND IDMOV = @idMov
            AND CODTMV = '1.1.10';
        `);

      if (!headerCheck.recordset || headerCheck.recordset.length === 0) {
        throw new MovementNotFoundError(idMov);
      }

      const itemsResult = await pool
        .request()
        .input('codColigada', sql.SmallInt, targetColigada)
        .input('idMov', sql.Int, idMov)
        .query(`
          SELECT
              I.CODCOLIGADA,
              I.IDMOV,
              I.NSEQITMMOV,
              I.IDPRD,
              ISNULL(P.CODIGOPRD, '') AS CODIGOPRD,
              ISNULL(P.NOMEFANTASIA, ISNULL(P.DESCRICAO, '')) AS DESCRICAOPRODUTO,
              ISNULL(I.CODUND, ISNULL(P.CODUNDCONTROLE, 'UN')) AS CODUND,
              CAST(
                ISNULL(
                  NULLIF(I.QUANTIDADETOTAL, 0),
                  ISNULL(NULLIF(I.QUANTIDADEORIGINAL, 0), ISNULL(I.QUANTIDADE, 0))
                ) AS DECIMAL(18, 4)
              ) AS QUANTIDADE,
              ISNULL(P.CODIGOAUXILIAR, '') AS CODIGOBARRA,
              '' AS OBSERVACAOITEM
          FROM [${this.corporeDb}].[dbo].[TITMMOV] I WITH (NOLOCK)
          INNER JOIN [${this.corporeDb}].[dbo].[TPRD] P WITH (NOLOCK)
              ON P.CODCOLIGADA = I.CODCOLIGADA
             AND P.IDPRD = I.IDPRD
          WHERE I.CODCOLIGADA = @codColigada
            AND I.IDMOV = @idMov
          ORDER BY I.NSEQITMMOV ASC;
        `);

      const rows = itemsResult.recordset || [];
      const mapped = rows.map((row, idx) => ({
        codColigada: targetColigada,
        idMov: Number(row.IDMOV) || idMov,
        nSeqItmMov: Number(row.NSEQITMMOV) || idx + 1,
        idPrd: Number(row.IDPRD) || 0,
        codigoPrd: String(row.CODIGOPRD || '').trim(),
        descricaoProduto: String(row.DESCRICAOPRODUTO || '').trim(),
        codUnd: String(row.CODUND || 'UN').trim(),
        quantidade: Number(row.QUANTIDADE) || 0,
        codigoBarra: String(row.CODIGOBARRA || '').trim(),
        observacaoItem: String(row.OBSERVACAOITEM || '').trim()
      }));

      if (this.itemsCache.size > 200) {
        this.itemsCache.clear();
      }
      this.itemsCache.set(idMov, {
        data: mapped,
        expiresAt: Date.now() + ITEMS_CACHE_TTL_MS
      });

      return mapped;
    } catch (err: unknown) {
      if (err instanceof MovementNotFoundError || err instanceof TotvsRmIntegrationError) {
        throw err;
      }
      throw new TotvsRmIntegrationError(
        `Erro ao consultar itens do movimento ${idMov} no ERP.`,
        err
      );
    }
  }

  async saveWithdrawalComplementaryFields(
    _codColigada: number,
    idMov: number,
    retiradoPor: string,
    dataRetirada: string,
    horaRetirada: string
  ): Promise<string> {
    // Fixado estritamente na Coligada 2 (Tecal Engenharia)
    const targetColigada = _codColigada > 0 ? _codColigada : (this.config.rmCodColigada || 2);
    const pool = await this.getPool();
    const transaction = new sql.Transaction(pool);

    try {
      await transaction.begin();

      const movResult = await new sql.Request(transaction)
        .input('codColigada', sql.SmallInt, targetColigada)
        .input('idMov', sql.Int, idMov)
        .query(`
          SELECT TOP 1
            CODCOLIGADA,
            IDMOV,
            ISNULL(NUMEROMOV, CAST(IDMOV AS VARCHAR(20))) AS NUMEROMOV,
            CODTMV,
            ISNULL(STATUS, '') AS STATUS
          FROM [${this.corporeDb}].[dbo].[TMOV] WITH (NOLOCK)
          WHERE CODCOLIGADA = @codColigada
            AND IDMOV = @idMov
            AND CODTMV = '1.1.10';
        `);

      const movRow = movResult.recordset?.[0];
      if (!movRow || String(movRow.STATUS).toUpperCase() === 'C') {
        await transaction.rollback();
        throw new MovementNotFoundError(idMov);
      }

      const realColigada = targetColigada;
      const numeroMov = String(movRow.NUMEROMOV || idMov).trim();

      // Resolve referência opaca 'rm_...' ou CPF numérico para identificador seguro "primeiro.ultimo" / login corporativo
      let safeRetiradoPor = await this.resolveSafeWithdrawerLabel(retiradoPor);
      const digitsOnly = onlyDigits(safeRetiradoPor);
      if (digitsOnly.length === 11 && digitsOnly === safeRetiradoPor) {
        try {
          const funcRes = await new sql.Request(transaction)
            .input('cpf', sql.VarChar(20), digitsOnly)
            .query(`
              SELECT TOP 1 LTRIM(RTRIM(F.NOME)) AS NOME_FUNC
              FROM [${this.corporeDb}].[dbo].[PFUNC] F WITH (NOLOCK)
              INNER JOIN [${this.corporeDb}].[dbo].[PPESSOA] P WITH (NOLOCK)
                ON P.CODIGO = F.CODPESSOA
              WHERE F.CODCOLIGADA = 2
                AND LTRIM(RTRIM(P.CPF)) = @cpf;
            `);
          const nomeFunc = String(funcRes.recordset?.[0]?.NOME_FUNC || '').trim();
          if (nomeFunc) {
            safeRetiradoPor = buildSafeNameSlug(nomeFunc);
          }
        } catch {
          // Mantém identificador caso falhe leitura auxiliar
        }
      }

      const complResult = await new sql.Request(transaction)
        .input('codColigada', sql.SmallInt, realColigada)
        .input('idMov', sql.Int, idMov)
        .query(`
          SELECT
            IDMOV,
            CAST([${this.colRetiradoPor}] AS VARCHAR(60)) AS RETIRADO_POR,
            CONVERT(VARCHAR(20), [${this.colDataRetirada}], 126) AS DATA_RETIRADA,
            CAST([${this.colHoraRetirada}] AS VARCHAR(20)) AS HORA_RETIRADA
          FROM [${this.corporeDb}].[dbo].[TMOVCOMPL] WITH (UPDLOCK, ROWLOCK, HOLDLOCK)
          WHERE CODCOLIGADA = @codColigada
             AND IDMOV = @idMov;
        `);

      const complRow = complResult.recordset?.[0];

      if (complRow) {
        const existingUser = String(complRow.RETIRADO_POR || '').trim();

        if (existingUser) {
          await transaction.rollback();
          throw new MovementAlreadyWithdrawnError(idMov, existingUser);
        }

        await new sql.Request(transaction)
          .input('codColigada', sql.SmallInt, realColigada)
          .input('idMov', sql.Int, idMov)
          .input('retiradoPor', sql.VarChar(60), safeRetiradoPor)
          .input('dataRetirada', sql.VarChar(30), dataRetirada)
          .input('horaRetirada', sql.VarChar(8), horaRetirada)
          .query(`
            UPDATE [${this.corporeDb}].[dbo].[TMOVCOMPL]
               SET [${this.colRetiradoPor}]  = @retiradoPor,
                   [${this.colDataRetirada}] = TRY_CONVERT(DATETIME, @dataRetirada, 120),
                   [${this.colHoraRetirada}] = @horaRetirada
             WHERE CODCOLIGADA = @codColigada
               AND IDMOV = @idMov;
          `);
      } else {
        await new sql.Request(transaction)
          .input('codColigada', sql.SmallInt, realColigada)
          .input('idMov', sql.Int, idMov)
          .input('retiradoPor', sql.VarChar(60), safeRetiradoPor)
          .input('dataRetirada', sql.VarChar(30), dataRetirada)
          .input('horaRetirada', sql.VarChar(8), horaRetirada)
          .query(`
            INSERT INTO [${this.corporeDb}].[dbo].[TMOVCOMPL]
              (CODCOLIGADA, IDMOV, [${this.colRetiradoPor}], [${this.colDataRetirada}], [${this.colHoraRetirada}])
            VALUES
              (@codColigada, @idMov, @retiradoPor, TRY_CONVERT(DATETIME, @dataRetirada, 120), @horaRetirada);
          `);
      }

      await transaction.commit();

      // Invalida o cache de movimentos para refletir a baixa imediatamente
      this.movementsCache = null;

      return numeroMov;
    } catch (err: unknown) {
      try {
        await transaction.rollback();
      } catch {
        // Ignora erro de rollback
      }

      if (
        err instanceof MovementNotFoundError ||
        err instanceof MovementAlreadyWithdrawnError ||
        err instanceof TotvsRmIntegrationError
      ) {
        throw err;
      }

      throw new TotvsRmIntegrationError(
        `Falha ao gravar baixa do movimento (IDMOV=${idMov}) no ERP.`,
        err
      );
    }
  }

  /**
   * Lista TODOS os funcionários ativos no TOTVS RM (Coligada 2) aplicando Minimização LGPD:
   * - CPF é mascarado no servidor ("158.***.***-**") antes de sair do BFF.
   * - Logins reais do RM / CPFs nunca trafegam pela rede: é enviada apenas referência opaca ('rm_...').
   */
  async listUsers(): Promise<AlmoxarifadoUserDto[]> {
    if (this.usersCache && Date.now() < this.usersCache.expiresAt) {
      return this.usersCache.data;
    }

    try {
      const pool = await this.getPool();
      const usersMap = new Map<string, AlmoxarifadoUserDto>();
      const nextInternalByRef = new Map<string, InternalUserRecord>();
      const nextInternalByCpf = new Map<string, InternalUserRecord>();
      const seenRealLogins = new Set<string>();
      let seqId = 1;

      try {
        // Busca colaboradores ativos na Coligada exclusivamente por CPF (11 dígitos) com conta ativa em GUSUARIO.CODUSUARIO = CPF.
        // Nunca busca nem expõe CODUSUARIO nomeado (ex.: raphael.araujo) ou 2-CHAPA.
        const targetColigada = this.config.rmCodColigada || 2;
        const rmCpfOnlyQuery = `
          SELECT
            LTRIM(RTRIM(F.CHAPA)) AS CHAPA,
            LTRIM(RTRIM(F.NOME)) AS NOME_FUNC,
            LTRIM(RTRIM(ISNULL(P.CPF, ''))) AS CPF,
            LTRIM(RTRIM(ISNULL(S.DESCRICAO, ''))) AS NOME_SECAO
          FROM [${this.corporeDb}].[dbo].[PFUNC] F WITH (NOLOCK)
          INNER JOIN [${this.corporeDb}].[dbo].[PPESSOA] P WITH (NOLOCK)
            ON P.CODIGO = F.CODPESSOA
          INNER JOIN [${this.corporeDb}].[dbo].[GUSUARIO] G WITH (NOLOCK)
            ON ISNULL(G.STATUS, 1) = 1
           AND LTRIM(RTRIM(G.CODUSUARIO)) = LTRIM(RTRIM(P.CPF))
          LEFT JOIN [${this.corporeDb}].[dbo].[PSECAO] S WITH (NOLOCK)
            ON S.CODCOLIGADA = F.CODCOLIGADA
           AND S.CODIGO = F.CODSECAO
          WHERE F.CODCOLIGADA = @codColigada
            AND ISNULL(F.CODSITUACAO, '') <> 'D'
            AND LEN(LTRIM(RTRIM(ISNULL(P.CPF, '')))) = 11
            AND LTRIM(RTRIM(P.CPF)) NOT LIKE '%[^0-9]%'
          ORDER BY F.NOME ASC, CASE WHEN F.CODSITUACAO = 'A' THEN 0 ELSE 1 END ASC, F.CHAPA DESC;
        `;

        const rmRes = await pool
          .request()
          .input('codColigada', sql.SmallInt, targetColigada)
          .query(rmCpfOnlyQuery);

        const rmRows = rmRes.recordset || [];
        for (const row of rmRows) {
          const chapa = String(row.CHAPA || '').trim();
          const nomeCompleto = String(row.NOME_FUNC || '').trim();
          const fullCpf = onlyDigits(String(row.CPF || ''));
          const secao = String(row.NOME_SECAO || '').trim();

          if (fullCpf.length !== 11 || !nomeCompleto) continue;
          // Deduplica colaboradores com mais de uma matrícula na PFUNC mantendo o vínculo principal
          if (nextInternalByCpf.has(fullCpf)) continue;

          const isEstoque =
            secao.toUpperCase().includes('ESTOQ') || secao.toUpperCase().includes('ALMOX');

          const opaqueRef = createOpaqueRmRef(
            this.config.jwtSecret,
            `cpf|${fullCpf}`
          );
          const maskedCpf = maskCpfLgpd(fullCpf);
          // Grava primeiro e último nome (slug seguro) na TMOVCOMPL.RETIRADO_POR sem expor CPF nem CODUSUARIO nomeado
          const safeLabel = buildSafeNameSlug(nomeCompleto);

          const publicDto: AlmoxarifadoUserDto = {
            id: seqId++,
            login: opaqueRef,
            nomeCompleto,
            cpf: maskedCpf,
            chapa,
            secao,
            origemRm: true,
            perfil: isEstoque ? 'ESTOQUE' : 'RETIRANTE',
            ativo: true,
            criadoEm: '',
            atualizadoEm: ''
          };

          const record: InternalUserRecord = {
            ref: opaqueRef,
            realRmLogin: fullCpf,
            candidateLogins: [fullCpf],
            fullCpf,
            chapa,
            safeLabel,
            publicDto
          };

          usersMap.set(opaqueRef, publicDto);
          nextInternalByRef.set(opaqueRef.toLowerCase(), record);
          nextInternalByCpf.set(fullCpf, record);
          seenRealLogins.add(fullCpf);
        }
      } catch {
        // Fallback caso PFUNC não esteja acessível
      }

      try {
        const localRes = await pool.request().query(`
          SELECT
            [ID],
            [LOGIN],
            [NOME_COMPLETO],
            UPPER(LTRIM(RTRIM([PERFIL]))) AS [PERFIL],
            [ATIVO],
            CONVERT(VARCHAR(19), [CRIADO_EM], 126) AS [CRIADO_EM],
            CONVERT(VARCHAR(19), [ATUALIZADO_EM], 126) AS [ATUALIZADO_EM]
          FROM [${this.almoxDb}].[dbo].[USUARIOS] WITH (NOLOCK)
          ORDER BY [NOME_COMPLETO] ASC;
        `);

        for (const row of localRes.recordset || []) {
          const rawLogin = String(row.LOGIN || '').trim();
          if (!rawLogin) continue;
          const key = rawLogin.toLowerCase();
          const fullCpf = isCpfNumericLogin(rawLogin) ? onlyDigits(rawLogin) : '';

          // Mantém apenas a conta técnica de sessão do tablet ('almoxarife') ou logins por CPF (11 dígitos);
          // ignora logins nomeados (ex.: Gilmar.Araujo, phelipe.carvalho) para não alimentar o app com CODUSUARIO nomeado.
          if (key !== 'almoxarife' && fullCpf.length !== 11) {
            continue;
          }

          if (!seenRealLogins.has(fullCpf || key) && !usersMap.has(key)) {
            const exposedLogin = fullCpf
              ? createOpaqueRmRef(this.config.jwtSecret, `local|${fullCpf}`)
              : rawLogin;
            const nomeCompleto = String(row.NOME_COMPLETO || '').trim();

            const publicDto: AlmoxarifadoUserDto = {
              id: Number(row.ID) || seqId++,
              login: exposedLogin,
              nomeCompleto,
              cpf: maskCpfLgpd(fullCpf),
              chapa: '',
              secao: '',
              origemRm: false,
              perfil: (String(row.PERFIL || 'RETIRANTE').trim() as UserProfileRole),
              ativo: Boolean(row.ATIVO),
              criadoEm: String(row.CRIADO_EM || ''),
              atualizadoEm: String(row.ATUALIZADO_EM || '')
            };

            usersMap.set(exposedLogin.toLowerCase(), publicDto);
            if (exposedLogin !== rawLogin) {
              const localRec: InternalUserRecord = {
                ref: exposedLogin,
                realRmLogin: fullCpf || rawLogin,
                candidateLogins: [fullCpf || rawLogin],
                fullCpf,
                chapa: '',
                safeLabel: buildSafeNameSlug(nomeCompleto),
                publicDto
              };
              nextInternalByRef.set(exposedLogin.toLowerCase(), localRec);
              if (fullCpf.length === 11) {
                nextInternalByCpf.set(fullCpf, localRec);
              }
            }
          }
        }
      } catch {
        // Ignora erro na tabela auxiliar
      }

      const finalUsers = Array.from(usersMap.values()).sort((a, b) =>
        a.nomeCompleto.localeCompare(b.nomeCompleto, 'pt-BR')
      );

      this.internalUsersByRef = nextInternalByRef;
      this.internalUsersByCpf = nextInternalByCpf;
      this.usersCache = {
        data: finalUsers,
        expiresAt: Date.now() + USERS_CACHE_TTL_MS
      };

      return finalUsers;
    } catch (err: unknown) {
      throw new TotvsRmIntegrationError(
        'Erro ao listar colaboradores ativos do ERP.',
        err
      );
    }
  }

  /**
   * Busca exata por CPF completo (11 dígitos) feita exclusivamente em memória no servidor,
   * retornando apenas o card já mascarado ("158.***.***-**") e referência opaca.
   * Impede enumeração dígito-a-dígito (sem oráculo para 4..10 dígitos).
   */
  async lookupUserByExactCpf(rawCpf: string): Promise<AlmoxarifadoUserDto[]> {
    const digits = onlyDigits(rawCpf);
    if (digits.length !== 11) {
      return [];
    }
    await this.ensureUsersLoaded();
    const matches: AlmoxarifadoUserDto[] = [];
    const seenPublicRefs = new Set<string>();
    for (const rec of this.internalUsersByRef.values()) {
      if (rec.fullCpf === digits && !seenPublicRefs.has(rec.publicDto.login)) {
        seenPublicRefs.add(rec.publicDto.login);
        matches.push(rec.publicDto);
      }
    }
    return matches;
  }

  async upsertUser(request: UpsertUserRequest): Promise<AlmoxarifadoUserDto> {
    const cleanLogin = (request.login || '').trim().toLowerCase();
    const cleanNome = (request.nomeCompleto || '').trim();
    const perfil = ((request.perfil || 'RETIRANTE').trim().toUpperCase() as UserProfileRole);
    const ativo = request.ativo !== undefined ? Boolean(request.ativo) : true;

    if (!cleanLogin) {
      throw new BusinessValidationError('O campo login é obrigatório.');
    }
    if (cleanLogin.startsWith('rm_')) {
      throw new BusinessValidationError(
        'Colaboradores integrados do TOTVS RM são somente leitura e não podem ser alterados por este terminal.'
      );
    }
    if (!cleanNome) {
      throw new BusinessValidationError('O campo nomeCompleto é obrigatório.');
    }
    if (!['ESTOQUE', 'RETIRANTE', 'AMBOS'].includes(perfil)) {
      throw new BusinessValidationError('O campo perfil deve ser ESTOQUE, RETIRANTE ou AMBOS.');
    }

    try {
      const pool = await this.getPool();

      const existingRes = await pool
        .request()
        .input('login', sql.VarChar(60), cleanLogin)
        .query(`
          SELECT TOP 1 [ID], [SENHA_HASH], [SALT]
          FROM [${this.almoxDb}].[dbo].[USUARIOS]
          WHERE LOWER(LTRIM(RTRIM([LOGIN]))) = @login;
        `);

      const existing = existingRes.recordset?.[0];

      let salt: string;
      let senhaHash: string;

      if (request.senha && request.senha.length > 0) {
        salt = PasswordHasher.generateSalt();
        senhaHash = PasswordHasher.hashPassword(request.senha, salt);
      } else if (existing) {
        salt = String(existing.SALT);
        senhaHash = String(existing.SENHA_HASH);
      } else {
        throw new BusinessValidationError('A senha é obrigatória para cadastrar um novo usuário.');
      }

      if (existing) {
        await pool
          .request()
          .input('login', sql.VarChar(60), cleanLogin)
          .input('nomeCompleto', sql.VarChar(120), cleanNome)
          .input('senhaHash', sql.VarChar(64), senhaHash)
          .input('salt', sql.VarChar(32), salt)
          .input('perfil', sql.VarChar(20), perfil)
          .input('ativo', sql.Bit, ativo ? 1 : 0)
          .query(`
            UPDATE [${this.almoxDb}].[dbo].[USUARIOS]
               SET [NOME_COMPLETO] = @nomeCompleto,
                   [SENHA_HASH]    = @senhaHash,
                   [SALT]          = @salt,
                   [PERFIL]        = @perfil,
                   [ATIVO]         = @ativo,
                   [ATUALIZADO_EM] = SYSDATETIME()
             WHERE LOWER(LTRIM(RTRIM([LOGIN]))) = @login;
          `);
      } else {
        await pool
          .request()
          .input('login', sql.VarChar(60), cleanLogin)
          .input('nomeCompleto', sql.VarChar(120), cleanNome)
          .input('senhaHash', sql.VarChar(64), senhaHash)
          .input('salt', sql.VarChar(32), salt)
          .input('perfil', sql.VarChar(20), perfil)
          .input('ativo', sql.Bit, ativo ? 1 : 0)
          .query(`
            INSERT INTO [${this.almoxDb}].[dbo].[USUARIOS]
              ([LOGIN], [NOME_COMPLETO], [SENHA_HASH], [SALT], [PERFIL], [ATIVO])
            VALUES
              (@login, @nomeCompleto, @senhaHash, @salt, @perfil, @ativo);
          `);
      }

      this.usersCache = null;

      const savedRes = await pool
        .request()
        .input('login', sql.VarChar(60), cleanLogin)
        .query(`
          SELECT TOP 1
            [ID],
            [LOGIN],
            [NOME_COMPLETO],
            UPPER(LTRIM(RTRIM([PERFIL]))) AS [PERFIL],
            [ATIVO],
            CONVERT(VARCHAR(19), [CRIADO_EM], 126) AS [CRIADO_EM],
            CONVERT(VARCHAR(19), [ATUALIZADO_EM], 126) AS [ATUALIZADO_EM]
          FROM [${this.almoxDb}].[dbo].[USUARIOS] WITH (NOLOCK)
          WHERE LOWER(LTRIM(RTRIM([LOGIN]))) = @login;
        `);

      const row = savedRes.recordset[0];
      return {
        id: Number(row.ID),
        login: String(row.LOGIN).trim(),
        nomeCompleto: String(row.NOME_COMPLETO).trim(),
        cpf: '***.***.***-**',
        chapa: '',
        secao: '',
        origemRm: false,
        perfil: (String(row.PERFIL).trim() as UserProfileRole),
        ativo: Boolean(row.ATIVO),
        criadoEm: String(row.CRIADO_EM || ''),
        atualizadoEm: String(row.ATUALIZADO_EM || '')
      };
    } catch (err: unknown) {
      if (err instanceof BusinessValidationError || err instanceof TotvsRmIntegrationError) {
        throw err;
      }
      throw new TotvsRmIntegrationError(
        'Erro ao salvar usuário local de acesso ao terminal.',
        err
      );
    }
  }

  async updateUserStatus(login: string, ativo: boolean): Promise<AlmoxarifadoUserDto> {
    const cleanLogin = (login || '').trim().toLowerCase();
    if (!cleanLogin) {
      throw new BusinessValidationError('Informe o login do usuário.');
    }
    if (cleanLogin.startsWith('rm_')) {
      throw new BusinessValidationError(
        'Status de colaboradores integrados ao RM deve ser alterado diretamente no ERP.'
      );
    }

    try {
      const pool = await this.getPool();
      const updateRes = await pool
        .request()
        .input('login', sql.VarChar(60), cleanLogin)
        .input('ativo', sql.Bit, ativo ? 1 : 0)
        .query(`
          UPDATE [${this.almoxDb}].[dbo].[USUARIOS]
             SET [ATIVO] = @ativo,
                 [ATUALIZADO_EM] = SYSDATETIME()
           WHERE LOWER(LTRIM(RTRIM([LOGIN]))) = @login;

          SELECT TOP 1
            [ID],
            [LOGIN],
            [NOME_COMPLETO],
            UPPER(LTRIM(RTRIM([PERFIL]))) AS [PERFIL],
            [ATIVO],
            CONVERT(VARCHAR(19), [CRIADO_EM], 126) AS [CRIADO_EM],
            CONVERT(VARCHAR(19), [ATUALIZADO_EM], 126) AS [ATUALIZADO_EM]
          FROM [${this.almoxDb}].[dbo].[USUARIOS] WITH (NOLOCK)
          WHERE LOWER(LTRIM(RTRIM([LOGIN]))) = @login;
        `);

      this.usersCache = null;

      const row = updateRes.recordset?.[0];
      if (!row) {
        throw new BusinessValidationError(`Usuário '${cleanLogin}' não encontrado.`);
      }

      return {
        id: Number(row.ID),
        login: String(row.LOGIN).trim(),
        nomeCompleto: String(row.NOME_COMPLETO).trim(),
        cpf: '***.***.***-**',
        chapa: '',
        secao: '',
        origemRm: false,
        perfil: (String(row.PERFIL).trim() as UserProfileRole),
        ativo: Boolean(row.ATIVO),
        criadoEm: String(row.CRIADO_EM || ''),
        atualizadoEm: String(row.ATUALIZADO_EM || '')
      };
    } catch (err: unknown) {
      if (err instanceof BusinessValidationError || err instanceof TotvsRmIntegrationError) {
        throw err;
      }
      throw new TotvsRmIntegrationError(
        'Erro ao atualizar status do usuário local.',
        err
      );
    }
  }

  async close(): Promise<void> {
    if (this.poolPromise) {
      try {
        const pool = await this.poolPromise;
        await pool.close();
      } catch {
        // Ignora erro ao fechar pool
      } finally {
        this.poolPromise = null;
      }
    }
  }
}
