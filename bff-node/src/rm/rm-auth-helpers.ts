import crypto from 'node:crypto';
import { SoapXmlPayloads } from './soap-xml-payloads.js';

export interface EmployeeAliasSource {
  key: string;
  chapa: string;
  nomeCompleto: string;
  fullCpf: string;
  loginPpessoa: string;
  emailPessoa: string;
}

export interface GusuarioAliasSource {
  codUsuario: string;
  nome: string;
  email: string;
}

export type RmHostVerifyOutcome = 'VALID' | 'INVALID' | 'UNAVAILABLE';

/**
 * Normaliza nomes para comparação insensível a acentos, pontuação e múltiplos espaços.
 */
export function normalizePersonName(raw: string): string {
  return String(raw || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toUpperCase()
    .replace(/[^A-Z0-9\s-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Normalização fonética leve para tolerar grafias alternadas (Souza/Sousa, Y/I, PH/F, consoantes dobradas).
 */
export function normalizePersonNamePhonetic(raw: string): string {
  return normalizePersonName(raw)
    .replace(/\s+\d+-\d+$/, '')
    .replace(/Z/g, 'S')
    .replace(/Y/g, 'I')
    .replace(/PH/g, 'F')
    .replace(/LL/g, 'L')
    .replace(/SS/g, 'S')
    .replace(/TT/g, 'T')
    .replace(/NN/g, 'N')
    .trim();
}

function stripTrailingChapaTag(normalizedName: string): string {
  return normalizedName.replace(/\s+\d+-\d+$/, '').trim();
}

function isSimpleWordOverlapMatch(gWords: string[], eWords: string[]): boolean {
  if (gWords.length < 2 || eWords.length < 2) return false;
  if (gWords[0] !== eWords[0]) return false;

  const restG = gWords.slice(1).filter((w) => w.length >= 2);
  if (restG.length === 0) return false;

  const restE = eWords.slice(1).filter((w) => w.length >= 2);
  let matched = 0;
  for (const gw of restG) {
    const found = restE.some(
      (ew) =>
        ew === gw ||
        (gw.length >= 4 && ew.startsWith(gw)) ||
        (ew.length >= 4 && gw.startsWith(ew)) ||
        (gw.length >= 5 && ew.length >= 5 && gw.slice(0, 3) === ew.slice(0, 3) && gw.slice(-3) === ew.slice(-3))
    );
    if (found) matched++;
  }

  // Exige correspondência completa para nomes curtos (2 palavras) ou maioria forte em nomes longos
  return restG.length === 1 ? matched === 1 : matched >= Math.min(2, restG.length);
}

/**
 * Mapeia com segurança todos os logins ativos do GUSUARIO pertencentes a cada colaborador,
 * evitando homônimos e cobrindo logins ERP nomeados (com ou sem tag de chapa), CPF e contas '2-CHAPA'.
 */
export function buildEmployeeCandidateLoginsMap(
  employees: EmployeeAliasSource[],
  gusuarios: GusuarioAliasSource[]
): {
  loginsByEmployeeKey: Map<string, string[]>;
  primaryNamedByEmployeeKey: Map<string, string>;
} {
  const loginsByEmployeeKey = new Map<string, string[]>();
  const primaryNamedByEmployeeKey = new Map<string, string>();
  const gByLower = new Map<string, GusuarioAliasSource>();
  const assignedGLogins = new Set<string>();

  for (const g of gusuarios) {
    const clean = (g.codUsuario || '').trim();
    if (clean) gByLower.set(clean.toLowerCase(), g);
  }

  const addCandidate = (empKey: string, login: string, isNamed = false): void => {
    const clean = (login || '').trim();
    if (!clean) return;
    const list = loginsByEmployeeKey.get(empKey) || [];
    if (!list.some((existing) => existing.toLowerCase() === clean.toLowerCase())) {
      list.push(clean);
      loginsByEmployeeKey.set(empKey, list);
    }
    if (isNamed && !primaryNamedByEmployeeKey.has(empKey)) {
      primaryNamedByEmployeeKey.set(empKey, clean);
    }
  };

  // Índices estritos para evitar associação errônea entre homônimos
  const emailToEmps = new Map<string, EmployeeAliasSource[]>();
  const strictNameToEmps = new Map<string, EmployeeAliasSource[]>();

  for (const emp of employees) {
    const email = (emp.emailPessoa || '').trim().toLowerCase();
    if (email && email.includes('@')) {
      const list = emailToEmps.get(email) || [];
      list.push(emp);
      emailToEmps.set(email, list);
    }
    const cleanName = normalizePersonName(emp.nomeCompleto);
    if (cleanName) {
      const list = strictNameToEmps.get(cleanName) || [];
      list.push(emp);
      strictNameToEmps.set(cleanName, list);
    }
  }

  // 1. Associação por tags de chapa (GUSUARIO.NOME contendo 2-<chapa>) e logins determinísticos (PPESSOA, CPF, 2-CHAPA)
  const gByChapaTag = new Map<string, GusuarioAliasSource[]>();
  for (const g of gusuarios) {
    const m = /(?:^|\s)\d-(\d{4,8})\b/.exec(g.nome || '');
    if (m) {
      const tagChapa = m[1].replace(/^0+/, '') || '0';
      const list = gByChapaTag.get(tagChapa) || [];
      list.push(g);
      gByChapaTag.set(tagChapa, list);
    }
  }

  for (const emp of employees) {
    const normChapa = emp.chapa.replace(/^0+/, '') || '0';
    for (const g of gByChapaTag.get(normChapa) || []) {
      const cleanG = g.codUsuario.trim();
      const lowerG = cleanG.toLowerCase();
      // Garante que a tag 2-<chapa> pertence ao mesmo colaborador (primeiro nome bate) quando chapa reciclada/coligada 1
      const gFirst = normalizePersonName(g.nome).split(' ')[0];
      const eFirst = normalizePersonName(emp.nomeCompleto).split(' ')[0];
      const exactColigada2Tag = new RegExp(`2-0*${normChapa}\\b`).test(g.nome || '');
      if (exactColigada2Tag && gFirst && eFirst && gFirst === eFirst) {
        addCandidate(emp.key, cleanG, !/^\d+$/.test(cleanG) && !lowerG.startsWith('2-'));
        assignedGLogins.add(lowerG);
      }
    }

    if (emp.loginPpessoa) {
      addCandidate(emp.key, emp.loginPpessoa, false);
      assignedGLogins.add(emp.loginPpessoa.trim().toLowerCase());
    }
    if (emp.fullCpf.length === 11) {
      addCandidate(emp.key, emp.fullCpf, false);
      assignedGLogins.add(emp.fullCpf);
    }
    if (emp.chapa) {
      const twoChapa = `2-${emp.chapa}`;
      if (gByLower.has(twoChapa.toLowerCase())) {
        addCandidate(emp.key, gByLower.get(twoChapa.toLowerCase())!.codUsuario, false);
        assignedGLogins.add(twoChapa.toLowerCase());
      }
      addCandidate(emp.key, emp.chapa, false);
    }
  }

  // 2. Associação unívoca para contas ERP nomeadas sem tag atual de chapa (ex.: chapa transferida, nome truncado ou grafia Souza/Sousa)
  for (const g of gusuarios) {
    const cleanG = (g.codUsuario || '').trim();
    const lowerG = cleanG.toLowerCase();
    if (!cleanG || assignedGLogins.has(lowerG)) continue;
    if (/^\d{11}$/.test(cleanG) || /^2-\d+$/.test(lowerG)) continue;

    // 2a. E-mail exato e único entre ativos
    const gEmail = (g.email || '').trim().toLowerCase();
    if (gEmail && gEmail.includes('@')) {
      const emailHits = emailToEmps.get(gEmail) || [];
      if (emailHits.length === 1) {
        addCandidate(emailHits[0].key, cleanG, true);
        assignedGLogins.add(lowerG);
        continue;
      }
    }

    // 2b. Nome exato / prefixo longo (>= 22 chars sem a tag de chapa)
    const cleanGNome = stripTrailingChapaTag(normalizePersonName(g.nome));
    if (cleanGNome.length >= 10) {
      const exactHits = strictNameToEmps.get(cleanGNome) || [];
      if (exactHits.length === 1) {
        addCandidate(exactHits[0].key, cleanG, true);
        assignedGLogins.add(lowerG);
        continue;
      }

      if (cleanGNome.length >= 22) {
        const prefixHits = employees.filter((emp) =>
          normalizePersonName(emp.nomeCompleto).startsWith(cleanGNome)
        );
        if (prefixHits.length === 1) {
          addCandidate(prefixHits[0].key, cleanG, true);
          assignedGLogins.add(lowerG);
          continue;
        }
      }
    }

    // 2c. Comparação fonética/palavras apenas quando houver correspondência única entre os colaboradores ativos
    const gPhoneticWords = normalizePersonNamePhonetic(g.nome)
      .split(' ')
      .filter(Boolean);
    if (gPhoneticWords.length >= 2) {
      const softHits = employees.filter((emp) => {
        const ePhoneticWords = normalizePersonNamePhonetic(emp.nomeCompleto)
          .split(' ')
          .filter(Boolean);
        return isSimpleWordOverlapMatch(gPhoneticWords, ePhoneticWords);
      });
      if (softHits.length === 1) {
        addCandidate(softHits[0].key, cleanG, true);
        assignedGLogins.add(lowerG);
      }
    }
  }

  return { loginsByEmployeeKey, primaryNamedByEmployeeKey };
}

/**
 * Resolve a URL HTTP/REST/SOAP real do RM Host a partir de RM_BASE_URL e DB_HOST,
 * corrigindo o fallback legado da porta 1433 do SQL Server para a porta padrão 8051 do RM Host.
 */
export function resolveRmHttpBaseUrl(rmBaseUrl: string, dbHost: string, defaultPort = 8051): string {
  const clean = (rmBaseUrl || '').trim().replace(/\/+$/, '');
  const cleanHost = (dbHost || '192.168.7.7').trim();
  if (!clean) {
    return `http://${cleanHost}:${defaultPort}`;
  }
  try {
    const parsed = new URL(clean.includes('://') ? clean : `http://${clean}`);
    if (parsed.port === '1433' || !parsed.hostname) {
      return `http://${parsed.hostname || cleanHost}:${defaultPort}`;
    }
    return `${parsed.protocol}//${parsed.host}${parsed.pathname.replace(/\/+$/, '')}`;
  } catch {
    return `http://${cleanHost}:${defaultPort}`;
  }
}

export type FetchLike = typeof fetch;

/**
 * Cliente de validação de credenciais diretamente no servidor TOTVS RM (REST /api/connect/token + SOAP IwsBase),
 * com proteção anti-bloqueio de conta (NUMLOGININVALIDO) e deduplicação de cliques repetidos.
 */
export class RmHostAuthClient {
  private readonly failedAttemptsByUser = new Map<string, number[]>();
  private readonly recentInvalidPairs = new Map<string, number>();

  private static readonly FAILURE_WINDOW_MS = 5 * 60_000;
  private static readonly MAX_REMOTE_FAILURES_PER_WINDOW = 3;
  private static readonly IDENTICAL_PAIR_COOLDOWN_MS = 20_000;

  constructor(
    private readonly rmHttpBaseUrl: string,
    private readonly fetchImpl: FetchLike = fetch
  ) {}

  private buildPairDigest(login: string, senha: string): string {
    return crypto
      .createHash('sha256')
      .update(`${login.trim().toLowerCase()}\n${senha}`)
      .digest('hex');
  }

  private pruneAndCheckAllowed(loginLower: string, pairDigest: string, now: number): boolean {
    const cachedExpiry = this.recentInvalidPairs.get(pairDigest);
    if (cachedExpiry && now < cachedExpiry) {
      return false;
    }
    if (cachedExpiry) {
      this.recentInvalidPairs.delete(pairDigest);
    }

    const recent = (this.failedAttemptsByUser.get(loginLower) || []).filter(
      (ts) => now - ts < RmHostAuthClient.FAILURE_WINDOW_MS
    );
    if (recent.length > 0) {
      this.failedAttemptsByUser.set(loginLower, recent);
    } else {
      this.failedAttemptsByUser.delete(loginLower);
    }

    return recent.length < RmHostAuthClient.MAX_REMOTE_FAILURES_PER_WINDOW;
  }

  private recordFailure(loginLower: string, pairDigest: string, now: number): void {
    const recent = (this.failedAttemptsByUser.get(loginLower) || []).filter(
      (ts) => now - ts < RmHostAuthClient.FAILURE_WINDOW_MS
    );
    recent.push(now);
    this.failedAttemptsByUser.set(loginLower, recent);
    this.recentInvalidPairs.set(pairDigest, now + RmHostAuthClient.IDENTICAL_PAIR_COOLDOWN_MS);
  }

  private clearFailures(loginLower: string, pairDigest: string): void {
    this.failedAttemptsByUser.delete(loginLower);
    this.recentInvalidPairs.delete(pairDigest);
  }

  async verifyCredential(login: string, senha: string): Promise<boolean> {
    const cleanLogin = (login || '').trim();
    if (!cleanLogin || !senha || !this.rmHttpBaseUrl) return false;

    const loginLower = cleanLogin.toLowerCase();
    const now = Date.now();
    const pairDigest = this.buildPairDigest(cleanLogin, senha);

    if (!this.pruneAndCheckAllowed(loginLower, pairDigest, now)) {
      return false;
    }

    const restResult = await this.verifyViaConnectToken(cleanLogin, senha);
    if (restResult === 'VALID') {
      this.clearFailures(loginLower, pairDigest);
      return true;
    }
    if (restResult === 'INVALID') {
      this.recordFailure(loginLower, pairDigest, now);
      return false;
    }

    // Fallback para IwsBase/AutenticaAcesso somente se o endpoint REST /api/connect/token estiver indisponível
    const soapResult = await this.verifyViaSoapIwsBase(cleanLogin, senha);
    if (soapResult === 'VALID') {
      this.clearFailures(loginLower, pairDigest);
      return true;
    }
    if (soapResult === 'INVALID') {
      this.recordFailure(loginLower, pairDigest, now);
    }
    return false;
  }

  private async verifyViaConnectToken(login: string, senha: string): Promise<RmHostVerifyOutcome> {
    try {
      const response = await this.fetchImpl(`${this.rmHttpBaseUrl}/api/connect/token`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json'
        },
        body: JSON.stringify({
          UserName: login,
          Password: senha
        }),
        signal: AbortSignal.timeout(6_000)
      });

      if (response.ok) {
        const text = await response.text();
        if (!text) return 'VALID';
        try {
          const parsed = JSON.parse(text) as { AccessToken?: unknown; access_token?: unknown };
          if (parsed.AccessToken || parsed.access_token) {
            return 'VALID';
          }
        } catch {
          return 'VALID';
        }
        return 'INVALID';
      }

      if (response.status === 400 || response.status === 401 || response.status === 403) {
        return 'INVALID';
      }

      return 'UNAVAILABLE';
    } catch {
      return 'UNAVAILABLE';
    }
  }

  private async verifyViaSoapIwsBase(login: string, senha: string): Promise<RmHostVerifyOutcome> {
    try {
      const credentials = Buffer.from(`${login}:${senha}`, 'utf8').toString('base64');
      const response = await this.fetchImpl(`${this.rmHttpBaseUrl}/wsDataServer/IwsBase`, {
        method: 'POST',
        headers: {
          Authorization: `Basic ${credentials}`,
          SOAPAction: '"http://www.totvs.com/IwsBase/AutenticaAcesso"',
          'Content-Type': 'text/xml; charset=utf-8'
        },
        body: SoapXmlPayloads.buildAutenticaAcessoEnvelope(),
        signal: AbortSignal.timeout(6_000)
      });

      if (response.status === 401 || response.status === 403) {
        return 'INVALID';
      }
      if (!response.ok) {
        return 'UNAVAILABLE';
      }

      const body = await response.text();
      return SoapXmlPayloads.checkAutenticaAcessoSuccess(body) ? 'VALID' : 'INVALID';
    } catch {
      return 'UNAVAILABLE';
    }
  }
}

