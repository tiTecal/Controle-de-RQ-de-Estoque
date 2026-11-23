import dotenv from 'dotenv';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
  DEFAULT_APP_SIGNING_SECRET,
  hashAdminPin,
  resolveSafeJwtSecret
} from '../security/security.js';
import { resolveRmHttpBaseUrl } from '../rm/rm-auth-helpers.js';

// Tenta carregar .env local (no diretório atual ou na raiz de bff-node)
const currentDir = path.dirname(fileURLToPath(import.meta.url));
const candidatePaths = [
  path.resolve(process.cwd(), '.env'),
  path.resolve(currentDir, '../../.env')
];
const foundEnvPath = candidatePaths.find((p) => fs.existsSync(p));
if (foundEnvPath) {
  dotenv.config({ path: foundEnvPath });
} else {
  dotenv.config();
}

export interface AppConfig {
  port: number;
  timezone: string;
  jwtSecret: string;
  jwtIssuer: string;
  jwtAudience: string;
  jwtExpirationHours: number;
  rmMockMode: boolean;

  // Segurança & Anti-Engenharia Reversa
  enforceAppSignature?: boolean;
  appSigningSecret?: string;
  requireAdminAuth?: boolean;
  adminPinSha256?: string;
  corsAllowedOrigin?: string | boolean;

  // Conexão Direta ao SQL Server (192.168.7.7)
  dbHost: string;
  dbPort: number;
  dbUser: string;
  dbPassword: string;
  dbCorporeName: string;
  dbAlmoxName: string;
  dbEncrypt: boolean;
  dbTrustServerCertificate: boolean;

  // Parâmetros de Negócio do TOTVS RM na base Corpore
  rmCodColigada: number;
  rmMinDataEmissao: string;
  colRetiradoPor: string;
  colDataRetirada: string;
  colHoraRetirada: string;
  dataRetiradaPattern: string;

  // Mantidos para retrocompatibilidade com testes legados
  rmBaseUrl: string;
  rmCodSistemaSql: string;
  rmContextExtra: string;
  rmServiceUser: string;
  rmServicePassword: string;
  rmSentencaPendentes: string;
  rmSentencaItens: string;
  rmContextoDataServer: string;
}

function getEnv(key: string, defaultValue: string): string {
  const val = process.env[key];
  return val && val.trim() !== '' ? val.trim() : defaultValue;
}

function parseHostFromBaseUrl(baseUrl: string, fallbackHost: string): string {
  try {
    const u = new URL(baseUrl.includes('://') ? baseUrl : `http://${baseUrl}`);
    return u.hostname || fallbackHost;
  } catch {
    return fallbackHost;
  }
}

export function loadAppConfig(): AppConfig {
  const rawColigada = parseInt(getEnv('RM_COD_COLIGADA', '2'), 10);
  const rmCodColigada = isNaN(rawColigada) || rawColigada <= 0 ? 2 : rawColigada;
  const rmContextExtra = getEnv('RM_CONTEXT_EXTRA', 'CODFILIAL=1;CODSISTEMA=T').replace(
    /^;+|;+$/g,
    ''
  );
  const rawRmBaseUrl = getEnv('RM_BASE_URL', '');
  const rmServiceUser = getEnv('RM_SERVICE_USER', 'almoxarife');
  const rmServicePassword = getEnv('RM_SERVICE_PASSWORD', '');

  const dbHost = getEnv(
    'DB_HOST',
    parseHostFromBaseUrl(rawRmBaseUrl || 'http://192.168.7.7:8051', '192.168.7.7')
  );
  const rmBaseUrl = resolveRmHttpBaseUrl(rawRmBaseUrl, dbHost, 8051).replace(/\/+$/, '');
  const dbPort = parseInt(getEnv('DB_PORT', '1433'), 10) || 1433;
  const dbUser = getEnv('DB_USER', rmServiceUser);
  const dbPassword = getEnv('DB_PASSWORD', rmServicePassword);
  const dbCorporeName = getEnv('DB_CORPORE_NAME', 'Corpore');
  const dbAlmoxName = getEnv('DB_ALMOX_NAME', 'ALMOXARIFADO');

  const isTest = process.env.NODE_ENV === 'test' || process.env.VITEST === 'true';
  const jwtResolved = resolveSafeJwtSecret(process.env.BFF_JWT_SECRET, isTest);
  if (jwtResolved.usedEphemeralFallback && !isTest) {
    console.warn(
      '[SEGURANCA] BFF_JWT_SECRET ausente ou usando segredo padrão inseguro. Chave criptográfica aleatória gerada em memória para esta execução.'
    );
  }

  const rawAdminPin = getEnv('BFF_ADMIN_PIN', '');
  const adminPinSha256 = rawAdminPin
    ? hashAdminPin(rawAdminPin)
    : getEnv(
        'BFF_ADMIN_PIN_SHA256',
        'd64d6aa9de9d8e1cafc04bdf82ff232ea010a90f88f64a16f837176b9675ef8f'
      ).toLowerCase();

  const corsRaw = getEnv('BFF_CORS_ORIGIN', 'false');
  const corsAllowedOrigin =
    corsRaw.toLowerCase() === 'false'
      ? false
      : corsRaw.toLowerCase() === 'true'
        ? true
        : corsRaw;

  return {
    port: parseInt(getEnv('BFF_PORT', '8080'), 10) || 8080,
    timezone: getEnv('BFF_TIMEZONE', 'America/Sao_Paulo'),
    jwtSecret: jwtResolved.secret,
    jwtIssuer: getEnv('BFF_JWT_ISSUER', 'tecal-almoxarifado-bff'),
    jwtAudience: getEnv('BFF_JWT_AUDIENCE', 'tecal-tablet-kiosk'),
    jwtExpirationHours: parseInt(getEnv('BFF_JWT_EXPIRATION_HOURS', '24'), 10) || 24,
    rmMockMode: getEnv('RM_MOCK_MODE', 'false').toLowerCase() === 'true',

    enforceAppSignature: getEnv('BFF_ENFORCE_APP_SIGNATURE', 'true').toLowerCase() === 'true',
    appSigningSecret: getEnv('BFF_APP_SIGNING_SECRET', DEFAULT_APP_SIGNING_SECRET),
    requireAdminAuth: getEnv('BFF_REQUIRE_ADMIN_AUTH', 'true').toLowerCase() === 'true',
    adminPinSha256,
    corsAllowedOrigin,

    dbHost,
    dbPort,
    dbUser,
    dbPassword,
    dbCorporeName,
    dbAlmoxName,
    dbEncrypt: getEnv('DB_ENCRYPT', 'false').toLowerCase() === 'true',
    dbTrustServerCertificate:
      getEnv('DB_TRUST_SERVER_CERTIFICATE', 'true').toLowerCase() === 'true',

    rmCodColigada,
    rmMinDataEmissao: getEnv('RM_MIN_DATA_EMISSAO', ''),
    colRetiradoPor: getEnv('RM_COL_RETIRADO_POR', 'RETIRADO_POR').toUpperCase(),
    colDataRetirada: getEnv('RM_COL_DATA_RETIRADA', 'DATA_RETIRADA').toUpperCase(),
    colHoraRetirada: getEnv('RM_COL_HORA_RETIRADA', 'HORA_RETIRADA').toUpperCase(),
    dataRetiradaPattern: getEnv('RM_DATA_RETIRADA_PATTERN', 'yyyy-MM-dd'),

    rmBaseUrl,
    rmCodSistemaSql: getEnv('RM_COD_SISTEMA_SQL', 'T'),
    rmContextExtra,
    rmServiceUser: dbUser,
    rmServicePassword: dbPassword,
    rmSentencaPendentes: getEnv('RM_SENTENCA_PENDENTES', 'TECAL.ALM.1110_PENDENTES'),
    rmSentencaItens: getEnv('RM_SENTENCA_ITENS', 'TECAL.ALM.1110_ITENS'),
    get rmContextoDataServer() {
      return `CODCOLIGADA=${rmCodColigada};${rmContextExtra};CODUSUARIO=${dbUser}`;
    }
  };
}
