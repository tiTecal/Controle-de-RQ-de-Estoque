import {
  AlmoxarifadoUserDto,
  MovementItemDto,
  PendingMovementDto,
  UpsertUserRequest
} from '../domain/types.js';

export interface TotvsRmGateway {
  /**
   * Valida login e senha de um usuário na base ALMOXARIFADO (dbo.USUARIOS).
   * Usado tanto no login inicial da sessão do estoque no tablet (requiredContext='ESTOQUE')
   * quanto na confirmação de retirada pelo colaborador (requiredContext='RETIRANTE').
   */
  authenticateRmUser(
    login: string,
    senha: string,
    requiredContext?: 'ESTOQUE' | 'RETIRANTE'
  ): Promise<boolean>;

  /**
   * Lista todos os movimentos 1.1.10 ativos na base Corpore que ainda NÃO têm os campos
   * complementares (retirado_por, data_retirada, hora_retirada) preenchidos na TMOVCOMPL.
   */
  listPending1110Movements(codColigada: number): Promise<PendingMovementDto[]>;

  /**
   * Lista 100% dos itens vinculados ao movimento 1.1.10 selecionado pelo estoquista.
   */
  listMovement1110Items(codColigada: number, idMov: number): Promise<MovementItemDto[]>;

  /**
   * Salva retirado_por, data_retirada e hora_retirada nos campos complementares (TMOVCOMPL)
   * do movimento 1.1.10 diretamente na base Corpore via transação SQL (UPDATE / INSERT).
   * Retorna o número do movimento (NUMEROMOV) atualizado.
   */
  saveWithdrawalComplementaryFields(
    codColigada: number,
    idMov: number,
    retiradoPor: string,
    dataRetirada: string,
    horaRetirada: string
  ): Promise<string>;

  /**
   * Gestão de usuários na base ALMOXARIFADO (dbo.USUARIOS) e listagem mascarada do RM.
   */
  listUsers?(): Promise<AlmoxarifadoUserDto[]>;
  lookupUserByExactCpf?(cpf: string): Promise<AlmoxarifadoUserDto[]>;
  resolveSafeWithdrawerLabel?(loginOrRef: string): Promise<string>;
  upsertUser?(request: UpsertUserRequest): Promise<AlmoxarifadoUserDto>;
  updateUserStatus?(login: string, ativo: boolean): Promise<AlmoxarifadoUserDto>;

  /**
   * Encerra conexões abertas (ex: pool SQL Server) ao desligar o servidor.
   */
  close?(): Promise<void>;
}
