export type UserProfileRole = 'ESTOQUE' | 'RETIRANTE' | 'AMBOS';

export interface EstoqueLoginRequest {
  login: string;
  senha: string;
}

export interface EstoqueLoginResponse {
  token: string;
  usuarioEstoque: string;
  expiraEmHoras: number;
  modoSimulacaoRm: boolean;
}

export interface PendingMovementDto {
  codColigada: number;
  idMov: number;
  numeroMov: string;
  codTmv: string;
  codFilial: number;
  codLoc: string;
  nomeLocal: string;
  dataEmissao: string;
  usuarioEstoquista: string;
  codCCusto: string;
  nomeCCusto: string;
  numeroRequisicao1113: string;
  solicitante: string;
  qtdItens: number;
  statusRm?: string;
  jaRetirado?: boolean;
  retiradoPor?: string;
  dataRetirada?: string;
  horaRetirada?: string;
}

export interface MovementItemDto {
  codColigada: number;
  idMov: number;
  nSeqItmMov: number;
  idPrd: number;
  codigoPrd: string;
  descricaoProduto: string;
  codUnd: string;
  quantidade: number;
  codigoBarra: string;
  observacaoItem: string;
}

export interface ConfirmWithdrawalRequest {
  login: string;
  senha: string;
  confirmado100Porcento: boolean;
  idempotencyKey: string;
}

export interface ConfirmWithdrawalResponse {
  codColigada: number;
  idMov: number;
  numeroMov: string;
  retiradoPor: string;
  dataRetirada: string;
  horaRetirada: string;
  mensagem: string;
}

export interface HealthResponse {
  status: string;
  rmMockMode: boolean;
  codColigada: number;
  timestampServidor: string;
  serverEpochMs?: number;
}

export interface ApiErrorResponse {
  codigo: string;
  mensagem: string;
}

export interface JwtEstoquePayload {
  sub: string;
  estoqueUser: string;
  iss: string;
  aud: string;
  exp?: number;
}

export interface AlmoxarifadoUserDto {
  id: number;
  login: string;
  nomeCompleto: string;
  cpf?: string;
  chapa?: string;
  secao?: string;
  origemRm?: boolean;
  perfil: UserProfileRole;
  ativo: boolean;
  criadoEm: string;
  atualizadoEm: string;
}

export interface LookupCpfRequest {
  cpf: string;
}

export interface VerifyAdminPinRequest {
  pin: string;
}

export interface VerifyAdminPinResponse {
  valid: boolean;
  adminToken: string;
  expiraEmMinutos: number;
}

export interface UpsertUserRequest {
  login: string;
  nomeCompleto: string;
  senha?: string;
  perfil?: UserProfileRole;
  ativo?: boolean;
}

export interface UpdateUserStatusRequest {
  ativo: boolean;
}
