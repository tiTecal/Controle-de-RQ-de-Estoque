import {
  BusinessValidationError,
  MovementAlreadyWithdrawnError,
  MovementNotFoundError
} from '../domain/errors.js';
import {
  AlmoxarifadoUserDto,
  MovementItemDto,
  PendingMovementDto,
  UpsertUserRequest,
  UserProfileRole
} from '../domain/types.js';
import { PasswordHasher } from './password-hasher.js';
import { TotvsRmGateway } from './totvs-rm-gateway.interface.js';

interface MockMovementRecord {
  header: PendingMovementDto;
  items: MovementItemDto[];
  retiradoPor?: string | null;
  dataRetirada?: string | null;
  horaRetirada?: string | null;
}

interface MockUserRecord {
  id: number;
  login: string;
  nomeCompleto: string;
  senhaHash: string;
  salt: string;
  perfil: UserProfileRole;
  ativo: boolean;
  criadoEm: string;
  atualizadoEm: string;
}

/**
 * Simulador realista em memória das bases Corpore e ALMOXARIFADO da Tecal para homologação
 * e testes automatizados (ativado quando RM_MOCK_MODE=true).
 *
 * Senhas aceitas no modo Mock para logins não cadastrados explicitamente: "tecal123" ou "rm123".
 * Usuários cadastrados via upsertUser validam hash SHA-256 + Salt e regras de perfil (ESTOQUE / RETIRANTE / AMBOS).
 */
export class MockTotvsRmClient implements TotvsRmGateway {
  private readonly movements = new Map<number, MockMovementRecord>();
  private readonly users = new Map<string, MockUserRecord>();
  private nextUserId = 1;

  constructor(defaultColigada: number = 1) {
    this.seedSampleUsers();
    this.seedSampleTecalMovements(defaultColigada);
  }

  async authenticateRmUser(
    login: string,
    senha: string,
    requiredContext?: 'ESTOQUE' | 'RETIRANTE'
  ): Promise<boolean> {
    const cleanLogin = login.trim().toLowerCase();
    if (!cleanLogin || !senha) return false;

    const registered = this.users.get(cleanLogin);
    if (registered) {
      if (!registered.ativo) return false;

      if (requiredContext === 'ESTOQUE') {
        if (registered.perfil !== 'ESTOQUE' && registered.perfil !== 'AMBOS') {
          return false;
        }
      } else if (requiredContext === 'RETIRANTE') {
        if (registered.perfil !== 'RETIRANTE' && registered.perfil !== 'AMBOS') {
          return false;
        }
      }

      return PasswordHasher.verifyPassword(senha, registered.salt, registered.senhaHash);
    }

    // Fallback para logins genéricos usados nos testes legados
    return senha === 'tecal123' || senha === 'rm123';
  }

  async listPending1110Movements(codColigada: number): Promise<PendingMovementDto[]> {
    const list: PendingMovementDto[] = [];
    for (const record of this.movements.values()) {
      if (
        record.header.codColigada === codColigada &&
        (!record.retiradoPor || !record.dataRetirada || !record.horaRetirada)
      ) {
        list.push(record.header);
      }
    }
    return list.sort((a, b) => b.idMov - a.idMov);
  }

  async listMovement1110Items(codColigada: number, idMov: number): Promise<MovementItemDto[]> {
    const record = this.movements.get(idMov);
    if (!record || record.header.codColigada !== codColigada) {
      throw new MovementNotFoundError(idMov);
    }
    return record.items;
  }

  async saveWithdrawalComplementaryFields(
    codColigada: number,
    idMov: number,
    retiradoPor: string,
    dataRetirada: string,
    horaRetirada: string
  ): Promise<string> {
    const record = this.movements.get(idMov);
    if (!record || record.header.codColigada !== codColigada) {
      throw new MovementNotFoundError(idMov);
    }

    if (record.retiradoPor && record.dataRetirada && record.horaRetirada) {
      throw new MovementAlreadyWithdrawnError(idMov, record.retiradoPor);
    }

    record.retiradoPor = retiradoPor;
    record.dataRetirada = dataRetirada;
    record.horaRetirada = horaRetirada;

    return record.header.numeroMov;
  }

  async listUsers(): Promise<AlmoxarifadoUserDto[]> {
    return Array.from(this.users.values())
      .sort((a, b) => a.login.localeCompare(b.login))
      .map((u) => ({
        id: u.id,
        login: u.login,
        nomeCompleto: u.nomeCompleto,
        perfil: u.perfil,
        ativo: u.ativo,
        criadoEm: u.criadoEm,
        atualizadoEm: u.atualizadoEm
      }));
  }

  async upsertUser(request: UpsertUserRequest): Promise<AlmoxarifadoUserDto> {
    const cleanLogin = (request.login || '').trim().toLowerCase();
    const cleanNome = (request.nomeCompleto || '').trim();
    const perfil = ((request.perfil || 'RETIRANTE').trim().toUpperCase() as UserProfileRole);
    const ativo = request.ativo !== undefined ? Boolean(request.ativo) : true;

    if (!cleanLogin) {
      throw new BusinessValidationError('O campo login é obrigatório.');
    }
    if (!cleanNome) {
      throw new BusinessValidationError('O campo nomeCompleto é obrigatório.');
    }
    if (!['ESTOQUE', 'RETIRANTE', 'AMBOS'].includes(perfil)) {
      throw new BusinessValidationError('O campo perfil deve ser ESTOQUE, RETIRANTE ou AMBOS.');
    }

    const existing = this.users.get(cleanLogin);
    const nowIso = new Date().toISOString().slice(0, 19);

    let salt: string;
    let senhaHash: string;

    if (request.senha && request.senha.length > 0) {
      salt = PasswordHasher.generateSalt();
      senhaHash = PasswordHasher.hashPassword(request.senha, salt);
    } else if (existing) {
      salt = existing.salt;
      senhaHash = existing.senhaHash;
    } else {
      throw new BusinessValidationError('A senha é obrigatória para cadastrar um novo usuário.');
    }

    const record: MockUserRecord = {
      id: existing ? existing.id : this.nextUserId++,
      login: cleanLogin,
      nomeCompleto: cleanNome,
      senhaHash,
      salt,
      perfil,
      ativo,
      criadoEm: existing ? existing.criadoEm : nowIso,
      atualizadoEm: nowIso
    };

    this.users.set(cleanLogin, record);

    return {
      id: record.id,
      login: record.login,
      nomeCompleto: record.nomeCompleto,
      perfil: record.perfil,
      ativo: record.ativo,
      criadoEm: record.criadoEm,
      atualizadoEm: record.atualizadoEm
    };
  }

  async updateUserStatus(login: string, ativo: boolean): Promise<AlmoxarifadoUserDto> {
    const cleanLogin = (login || '').trim().toLowerCase();
    const existing = this.users.get(cleanLogin);
    if (!existing) {
      throw new BusinessValidationError(`Usuário '${cleanLogin}' não encontrado.`);
    }

    existing.ativo = Boolean(ativo);
    existing.atualizadoEm = new Date().toISOString().slice(0, 19);

    return {
      id: existing.id,
      login: existing.login,
      nomeCompleto: existing.nomeCompleto,
      perfil: existing.perfil,
      ativo: existing.ativo,
      criadoEm: existing.criadoEm,
      atualizadoEm: existing.atualizadoEm
    };
  }

  private seedSampleUsers(): void {
    const nowIso = '2026-10-30T07:00:00';
    const salt1 = PasswordHasher.generateSalt();
    this.users.set('almoxarife', {
      id: this.nextUserId++,
      login: 'almoxarife',
      nomeCompleto: 'Marcos Estoque (Almoxarife)',
      salt: salt1,
      senhaHash: PasswordHasher.hashPassword('tecal123', salt1),
      perfil: 'AMBOS',
      ativo: true,
      criadoEm: nowIso,
      atualizadoEm: nowIso
    });

    const salt2 = PasswordHasher.generateSalt();
    this.users.set('carlos.silva', {
      id: this.nextUserId++,
      login: 'carlos.silva',
      nomeCompleto: 'Carlos Silva (Caldeiraria)',
      salt: salt2,
      senhaHash: PasswordHasher.hashPassword('tecal123', salt2),
      perfil: 'RETIRANTE',
      ativo: true,
      criadoEm: nowIso,
      atualizadoEm: nowIso
    });
  }

  private seedSampleTecalMovements(coligada: number): void {
    const m1: MockMovementRecord = {
      header: {
        codColigada: coligada,
        idMov: 45812,
        numeroMov: '009418',
        codTmv: '1.1.10',
        codFilial: 1,
        codLoc: '01.01',
        nomeLocal: 'Almoxarifado Central Tecal',
        dataEmissao: '2026-10-30T07:50:00',
        usuarioEstoquista: 'marcos.estoque',
        codCCusto: '3.02.01',
        nomeCCusto: 'Manutenção Industrial - Caldeiraria',
        numeroRequisicao1113: '014205',
        solicitante: 'carlos.silva',
        qtdItens: 3
      },
      items: [
        {
          codColigada: coligada,
          idMov: 45812,
          nSeqItmMov: 1,
          idPrd: 1042,
          codigoPrd: 'INS.00412',
          descricaoProduto: 'DISCO DE CORTE INOX 7" X 1/16" X 7/8"',
          codUnd: 'PC',
          quantidade: 10.0,
          codigoBarra: '7891234500412',
          observacaoItem: 'Separado bancada 02'
        },
        {
          codColigada: coligada,
          idMov: 45812,
          nSeqItmMov: 2,
          idPrd: 1089,
          codigoPrd: 'SOL.00108',
          descricaoProduto: 'ELETRODO REVESTIDO E7018 3,25MM (LATA 5KG)',
          codUnd: 'LT',
          quantidade: 2.0,
          codigoBarra: '7891234500108',
          observacaoItem: ''
        },
        {
          codColigada: coligada,
          idMov: 45812,
          nSeqItmMov: 3,
          idPrd: 2190,
          codigoPrd: 'EPI.00054',
          descricaoProduto: 'LUVA DE RASPA CANO LONGO REFORCADA',
          codUnd: 'PAR',
          quantidade: 2.0,
          codigoBarra: '7891234500054',
          observacaoItem: ''
        }
      ]
    };

    const m2: MockMovementRecord = {
      header: {
        codColigada: coligada,
        idMov: 45815,
        numeroMov: '009419',
        codTmv: '1.1.10',
        codFilial: 1,
        codLoc: '01.01',
        nomeLocal: 'Almoxarifado Central Tecal',
        dataEmissao: '2026-10-30T08:15:00',
        usuarioEstoquista: 'marcos.estoque',
        codCCusto: '4.01.05',
        nomeCCusto: 'Montagem Elétrica e Automação',
        numeroRequisicao1113: '014209',
        solicitante: 'fernanda.rocha',
        qtdItens: 2
      },
      items: [
        {
          codColigada: coligada,
          idMov: 45815,
          nSeqItmMov: 1,
          idPrd: 3310,
          codigoPrd: 'ELE.01240',
          descricaoProduto: 'CABO FLEXIVEL PP 4X2,5MM 1KV PRETO',
          codUnd: 'M',
          quantidade: 50.0,
          codigoBarra: '7891234501240',
          observacaoItem: 'Rolo cortado e etiquetado'
        },
        {
          codColigada: coligada,
          idMov: 45815,
          nSeqItmMov: 2,
          idPrd: 3402,
          codigoPrd: 'ELE.00890',
          descricaoProduto: 'DISJUNTOR TRIPOLAR DIN CURVA C 40A',
          codUnd: 'PC',
          quantidade: 3.0,
          codigoBarra: '7891234500890',
          observacaoItem: ''
        }
      ]
    };

    const m3: MockMovementRecord = {
      header: {
        codColigada: coligada,
        idMov: 45819,
        numeroMov: '009420',
        codTmv: '1.1.10',
        codFilial: 1,
        codLoc: '01.01',
        nomeLocal: 'Almoxarifado Central Tecal',
        dataEmissao: '2026-10-30T08:30:00',
        usuarioEstoquista: 'marcos.estoque',
        codCCusto: '2.04.02',
        nomeCCusto: 'Operação Campo - Obra Planta Sul',
        numeroRequisicao1113: '014212',
        solicitante: 'joao.souza',
        qtdItens: 4
      },
      items: [
        {
          codColigada: coligada,
          idMov: 45819,
          nSeqItmMov: 1,
          idPrd: 4100,
          codigoPrd: 'HID.00321',
          descricaoProduto: 'VALVULA ESFERA INOX 316 TRIPARTIDA 1.1/2" BSP',
          codUnd: 'PC',
          quantidade: 4.0,
          codigoBarra: '7891234500321',
          observacaoItem: ''
        },
        {
          codColigada: coligada,
          idMov: 45819,
          nSeqItmMov: 2,
          idPrd: 4125,
          codigoPrd: 'FIX.00912',
          descricaoProduto: 'ESTOJO PARAFUSO A193 B7 5/8" X 110MM C/ 2 PORCAS 2H',
          codUnd: 'CJ',
          quantidade: 24.0,
          codigoBarra: '7891234500912',
          observacaoItem: ''
        },
        {
          codColigada: coligada,
          idMov: 45819,
          nSeqItmMov: 3,
          idPrd: 4188,
          codigoPrd: 'VED.00210',
          descricaoProduto: 'JUNTA ESPIRALADA INOX/GRAFITE DN 4" CLASSE 150#',
          codUnd: 'PC',
          quantidade: 6.0,
          codigoBarra: '7891234500210',
          observacaoItem: ''
        },
        {
          codColigada: coligada,
          idMov: 45819,
          nSeqItmMov: 4,
          idPrd: 1520,
          codigoPrd: 'QUI.00045',
          descricaoProduto: 'DESENGRIPANTE SPRAY INDUSTRIAL 300ML',
          codUnd: 'FR',
          quantidade: 3.0,
          codigoBarra: '7891234500045',
          observacaoItem: ''
        }
      ]
    };

    this.movements.set(m1.header.idMov, m1);
    this.movements.set(m2.header.idMov, m2);
    this.movements.set(m3.header.idMov, m3);
  }
}
