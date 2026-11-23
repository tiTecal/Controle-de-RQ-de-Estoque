import { Mutex } from 'async-mutex';
import { DateTime } from 'luxon';
import { AppConfig } from '../config/env.js';
import {
  BusinessValidationError,
  InvalidRmCredentialsError,
  TotvsRmIntegrationError
} from '../domain/errors.js';
import {
  AlmoxarifadoUserDto,
  ConfirmWithdrawalRequest,
  ConfirmWithdrawalResponse,
  EstoqueLoginRequest,
  EstoqueLoginResponse,
  MovementItemDto,
  PendingMovementDto,
  UpsertUserRequest
} from '../domain/types.js';
import { TotvsRmGateway } from '../rm/totvs-rm-gateway.interface.js';

export type JwtSignerFn = (payload: {
  sub: string;
  estoqueUser: string;
  iss: string;
  aud: string;
}) => string;

export class WithdrawalService {
  private readonly movementLocks = new Map<number, Mutex>();
  private readonly idempotencyCache = new Map<string, ConfirmWithdrawalResponse>();

  constructor(
    private readonly config: AppConfig,
    private readonly rmGateway: TotvsRmGateway,
    private readonly jwtSigner?: JwtSignerFn
  ) {}

  /**
   * Autentica a sessão persistente do tablet (usuário do estoque) na base ALMOXARIFADO
   * para deixar o sistema logado ao lado da porta listando os movimentos 1.1.10 pendentes.
   */
  async loginSessaoEstoque(
    request: EstoqueLoginRequest,
    customSigner?: JwtSignerFn
  ): Promise<EstoqueLoginResponse> {
    const cleanLogin = (request.login || '').trim();
    if (!cleanLogin || !request.senha) {
      throw new BusinessValidationError('Informe o login e a senha do usuário do estoque.');
    }

    const authenticated = await this.rmGateway.authenticateRmUser(
      cleanLogin,
      request.senha,
      'ESTOQUE'
    );
    if (!authenticated) {
      throw new InvalidRmCredentialsError(
        'Credenciais do usuário do estoque inválidas ou sem perfil ESTOQUE.',
        false
      );
    }

    const signer = customSigner || this.jwtSigner;
    if (!signer) {
      throw new Error('JWT Signer não configurado no WithdrawalService.');
    }

    const token = signer({
      sub: cleanLogin,
      estoqueUser: cleanLogin,
      iss: this.config.jwtIssuer,
      aud: this.config.jwtAudience
    });

    return {
      token,
      usuarioEstoque: cleanLogin,
      expiraEmHoras: this.config.jwtExpirationHours,
      modoSimulacaoRm: this.config.rmMockMode
    };
  }

  async listarMovimentos1110Pendentes(): Promise<PendingMovementDto[]> {
    return await this.rmGateway.listPending1110Movements(this.config.rmCodColigada);
  }

  async listarItensMovimento1110(idMov: number): Promise<MovementItemDto[]> {
    if (isNaN(idMov) || idMov <= 0) {
      throw new BusinessValidationError(`IDMOV inválido: ${idMov}`);
    }
    return await this.rmGateway.listMovement1110Items(this.config.rmCodColigada, idMov);
  }

  /**
   * Efetua a baixa de retirada 100% total do movimento 1.1.10:
   * 1. Exige confirmação de 100% dos itens (sem baixa parcial).
   * 2. Autentica o login e a senha do usuário retirante na base ALMOXARIFADO (dbo.USUARIOS).
   * 3. Salva retirado_por, data_retirada e hora_retirada na Corpore.dbo.TMOVCOMPL do movimento 1.1.10.
   */
  async confirmarBaixaRetirada1110(
    idMov: number,
    request: ConfirmWithdrawalRequest
  ): Promise<ConfirmWithdrawalResponse> {
    if (isNaN(idMov) || idMov <= 0) {
      throw new BusinessValidationError('IDMOV inválido.');
    }
    if (!request.confirmado100Porcento) {
      throw new BusinessValidationError(
        'Regra Tecal: A baixa do movimento 1.1.10 só é permitida se 100% dos itens forem retirados.'
      );
    }
    const cleanLogin = (request.login || '').trim();
    if (!cleanLogin || !request.senha) {
      throw new BusinessValidationError(
        'Informe seu login e senha para confirmar a retirada.'
      );
    }

    const idempotencyKey = (request.idempotencyKey || '').trim();
    if (idempotencyKey && this.idempotencyCache.has(idempotencyKey)) {
      return this.idempotencyCache.get(idempotencyKey)!;
    }

    // Valida login e senha do funcionário retirante no TOTVS RM ANTES de adquirir o lock de gravação
    const withdrawerAuthenticated = await this.rmGateway.authenticateRmUser(
      cleanLogin,
      request.senha,
      'RETIRANTE'
    );
    if (!withdrawerAuthenticated) {
      throw new InvalidRmCredentialsError(
        'Senha do RM incorreta para o funcionário selecionado. A baixa não foi realizada.',
        true
      );
    }

    let mutex = this.movementLocks.get(idMov);
    if (!mutex) {
      mutex = new Mutex();
      this.movementLocks.set(idMov, mutex);
    }

    return await mutex.runExclusive(async () => {
      if (idempotencyKey && this.idempotencyCache.has(idempotencyKey)) {
        return this.idempotencyCache.get(idempotencyKey)!;
      }

      const now = DateTime.now().setZone(this.config.timezone);
      const dataRetirada = now.toFormat(this.config.dataRetiradaPattern);
      const horaRetirada = now.toFormat('HH:mm:ss');

      const safeRetiradoPor = this.rmGateway.resolveSafeWithdrawerLabel
        ? await this.rmGateway.resolveSafeWithdrawerLabel(cleanLogin)
        : cleanLogin;

      const numeroMov = await this.rmGateway.saveWithdrawalComplementaryFields(
        this.config.rmCodColigada,
        idMov,
        cleanLogin,
        dataRetirada,
        horaRetirada
      );

      const response: ConfirmWithdrawalResponse = {
        codColigada: this.config.rmCodColigada,
        idMov,
        numeroMov,
        retiradoPor: safeRetiradoPor || cleanLogin,
        dataRetirada,
        horaRetirada,
        mensagem: `Baixa de retirada do movimento 1.1.10 Nº ${numeroMov} registrada com sucesso.`
      };

      if (idempotencyKey) {
        if (this.idempotencyCache.size > 2_000) {
          this.idempotencyCache.clear();
        }
        this.idempotencyCache.set(idempotencyKey, response);
      }

      return response;
    });
  }

  async listarUsuarios(): Promise<AlmoxarifadoUserDto[]> {
    if (!this.rmGateway.listUsers) {
      throw new TotvsRmIntegrationError('Gestão de usuários não suportada pelo gateway atual.');
    }
    return await this.rmGateway.listUsers();
  }

  async buscarColaboradorPorCpfExato(cpf: string): Promise<AlmoxarifadoUserDto[]> {
    if (this.rmGateway.lookupUserByExactCpf) {
      return await this.rmGateway.lookupUserByExactCpf(cpf);
    }
    return [];
  }

  async salvarUsuario(request: UpsertUserRequest): Promise<AlmoxarifadoUserDto> {
    if (!this.rmGateway.upsertUser) {
      throw new TotvsRmIntegrationError('Gestão de usuários não suportada pelo gateway atual.');
    }
    return await this.rmGateway.upsertUser(request);
  }

  async alterarStatusUsuario(login: string, ativo: boolean): Promise<AlmoxarifadoUserDto> {
    if (!this.rmGateway.updateUserStatus) {
      throw new TotvsRmIntegrationError('Gestão de usuários não suportada pelo gateway atual.');
    }
    return await this.rmGateway.updateUserStatus(login, ativo);
  }
}
