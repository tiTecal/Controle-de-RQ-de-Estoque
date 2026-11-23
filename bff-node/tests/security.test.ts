import crypto from 'node:crypto';
import { describe, expect, it } from 'vitest';
import { buildApp } from '../src/app.js';
import { AppConfig } from '../src/config/env.js';
import { TotvsRmIntegrationError } from '../src/domain/errors.js';
import { MockTotvsRmClient } from '../src/rm/mock-totvs-rm-client.js';
import { TotvsRmGateway } from '../src/rm/totvs-rm-gateway.interface.js';
import {
  createOpaqueRmRef,
  DEFAULT_APP_SIGNING_SECRET,
  hashAdminPin,
  INSECURE_DEFAULT_JWT_SECRET,
  maskCpfLgpd,
  resolveSafeJwtSecret,
  verifyClientHmacSignature
} from '../src/security/security.js';

function createHardenedConfig(): AppConfig {
  return {
    port: 8080,
    timezone: 'America/Sao_Paulo',
    jwtSecret: crypto.randomBytes(64).toString('hex'),
    jwtIssuer: 'tecal-almoxarifado-bff',
    jwtAudience: 'tecal-tablet-kiosk',
    jwtExpirationHours: 24,
    rmMockMode: true,
    enforceAppSignature: true,
    appSigningSecret: DEFAULT_APP_SIGNING_SECRET,
    requireAdminAuth: true,
    adminPinSha256: hashAdminPin('9876'),
    corsAllowedOrigin: false,
    dbHost: '192.168.7.7',
    dbPort: 1433,
    dbUser: 'almoxarife',
    dbPassword: '123',
    dbCorporeName: 'Corpore',
    dbAlmoxName: 'ALMOXARIFADO',
    dbEncrypt: false,
    dbTrustServerCertificate: true,
    rmMinDataEmissao: '2026-01-01',
    rmBaseUrl: 'http://localhost:8051',
    rmCodColigada: 2,
    rmCodSistemaSql: 'T',
    rmContextExtra: 'CODFILIAL=1;CODSISTEMA=T',
    rmServiceUser: 'mestre',
    rmServicePassword: '123',
    rmSentencaPendentes: 'TECAL.ALM.1110_PENDENTES',
    rmSentencaItens: 'TECAL.ALM.1110_ITENS',
    colRetiradoPor: 'RETIRADO_POR',
    colDataRetirada: 'DATA_RETIRADA',
    colHoraRetirada: 'HORA_RETIRADA',
    dataRetiradaPattern: 'yyyy-MM-dd',
    rmContextoDataServer: 'CODCOLIGADA=2;CODFILIAL=1;CODSISTEMA=T;CODUSUARIO=mestre'
  };
}

function signHeaders(method: string, path: string, secret = DEFAULT_APP_SIGNING_SECRET) {
  const ts = Date.now().toString();
  const nonce = crypto.randomBytes(16).toString('hex');
  const cleanPath = path.split('?')[0];
  const canonical = `${method.toUpperCase()}\n${cleanPath}\n${ts}\n${nonce}`;
  const sig = crypto.createHmac('sha256', secret).update(canonical).digest('hex');
  return {
    'X-Tecal-Timestamp': ts,
    'X-Tecal-Nonce': nonce,
    'X-Tecal-Signature': sig
  };
}

describe('Segurança, LGPD e Anti-Engenharia Reversa', () => {
  it('mascara CPF na raiz para exibir apenas os 3 primeiros dígitos e gera referência opaca', () => {
    expect(maskCpfLgpd('158.942.310-88')).toBe('158.***.***-**');
    expect(maskCpfLgpd('12')).toBe('***.***.***-**');

    const ref = createOpaqueRmRef('secret-key', '00123|15894231088');
    expect(ref.startsWith('rm_')).toBe(true);
    expect(ref).not.toContain('15894231088');
  });

  it('rejeita segredo JWT padrão conhecido em produção gerando segredo efêmero forte', () => {
    const prodFallback = resolveSafeJwtSecret(INSECURE_DEFAULT_JWT_SECRET, false);
    expect(prodFallback.usedEphemeralFallback).toBe(true);
    expect(prodFallback.secret).not.toBe(INSECURE_DEFAULT_JWT_SECRET);
    expect(prodFallback.secret.length).toBeGreaterThanOrEqual(64);
  });

  it('bloqueia Replay Attack quando o mesmo nonce HMAC é reutilizado', () => {
    const usedNonces = new Map<string, number>();
    const ts = Date.now().toString();
    const nonce = crypto.randomBytes(16).toString('hex');
    const canonical = `GET\n/api/v1/movimentos/pendentes\n${ts}\n${nonce}`;
    const sig = crypto.createHmac('sha256', DEFAULT_APP_SIGNING_SECRET).update(canonical).digest('hex');

    const first = verifyClientHmacSignature({
      secret: DEFAULT_APP_SIGNING_SECRET,
      method: 'GET',
      path: '/api/v1/movimentos/pendentes',
      timestampHeader: ts,
      nonceHeader: nonce,
      signatureHeader: sig,
      usedNonces
    });
    expect(first.ok).toBe(true);

    const replay = verifyClientHmacSignature({
      secret: DEFAULT_APP_SIGNING_SECRET,
      method: 'GET',
      path: '/api/v1/movimentos/pendentes',
      timestampHeader: ts,
      nonceHeader: nonce,
      signatureHeader: sig,
      usedNonces
    });
    expect(replay.ok).toBe(false);
  });

  it('exige assinatura HMAC do aplicativo, bloqueia curl não assinado e protege rotas admin', async () => {
    const config = createHardenedConfig();
    const app = await buildApp({
      config,
      customGateway: new MockTotvsRmClient(2)
    });

    // 1. Chamada sem assinatura -> 403 UNAUTHORIZED_CLIENT
    const unsignedLogin = await app.inject({
      method: 'POST',
      url: '/api/v1/auth/login',
      payload: { login: 'almoxarife', senha: 'tecal123' }
    });
    expect(unsignedLogin.statusCode).toBe(403);
    expect(unsignedLogin.json().codigo).toBe('UNAUTHORIZED_CLIENT');

    // 2. Login devidamente assinado -> 200 OK e headers defensivos presentes
    const signedLogin = await app.inject({
      method: 'POST',
      url: '/api/v1/auth/login',
      headers: {
        ...signHeaders('POST', '/api/v1/auth/login'),
        Origin: 'https://evil-site.example'
      },
      payload: { login: 'almoxarife', senha: 'tecal123' }
    });
    expect(signedLogin.statusCode).toBe(200);
    expect(signedLogin.headers['x-frame-options']).toBe('DENY');
    expect(signedLogin.headers['x-content-type-options']).toBe('nosniff');
    expect(signedLogin.headers['access-control-allow-origin']).toBeUndefined();

    const token = signedLogin.json().token;

    // 3. Tentar criar usuário usando apenas JWT do quiosque (sem X-Tecal-Admin-Token) -> 403 ADMIN_AUTH_REQUIRED
    const unauthAdminPost = await app.inject({
      method: 'POST',
      url: '/api/v1/usuarios',
      headers: {
        Authorization: `Bearer ${token}`,
        ...signHeaders('POST', '/api/v1/usuarios')
      },
      payload: {
        login: 'invasor',
        nomeCompleto: 'Invasor',
        senha: '123',
        perfil: 'AMBOS'
      }
    });
    expect(unauthAdminPost.statusCode).toBe(403);
    expect(unauthAdminPost.json().codigo).toBe('ADMIN_AUTH_REQUIRED');

    // 4. Obter adminToken via PIN válido -> criar usuário passa -> 200 OK
    const pinRes = await app.inject({
      method: 'POST',
      url: '/api/v1/admin/verify-pin',
      headers: signHeaders('POST', '/api/v1/admin/verify-pin'),
      payload: { pin: '9876' }
    });
    expect(pinRes.statusCode).toBe(200);
    const adminToken = pinRes.json().adminToken;

    // 5. Tentar usar adminToken como Bearer token de sessão de estoque -> 401 SESSION_EXPIRED
    const misusingAdminToken = await app.inject({
      method: 'GET',
      url: '/api/v1/movimentos/pendentes',
      headers: {
        Authorization: `Bearer ${adminToken}`,
        ...signHeaders('GET', '/api/v1/movimentos/pendentes')
      }
    });
    expect(misusingAdminToken.statusCode).toBe(401);

    // 6. Criar usuário com JWT do quiosque + X-Tecal-Admin-Token -> 200 OK
    const authAdminPost = await app.inject({
      method: 'POST',
      url: '/api/v1/usuarios',
      headers: {
        Authorization: `Bearer ${token}`,
        'X-Tecal-Admin-Token': adminToken,
        ...signHeaders('POST', '/api/v1/usuarios')
      },
      payload: {
        login: 'operador.novo',
        nomeCompleto: 'Operador Novo',
        senha: 'SenhaSegura#2026',
        perfil: 'ESTOQUE'
      }
    });
    expect(authAdminPost.statusCode).toBe(200);

    await app.close();
  });

  it('nunca vaza IPs, portas ou nomes de tabelas RM em erros HTTP 502', async () => {
    const config = createHardenedConfig();
    config.enforceAppSignature = false;

    const brokenGateway: TotvsRmGateway = {
      async authenticateRmUser() {
        return true;
      },
      async listPending1110Movements() {
        throw new TotvsRmIntegrationError('SQL Server 192.168.7.7:1433 Corpore.dbo.TMOVCOMPL falhou');
      },
      async listMovement1110Items() {
        return [];
      },
      async saveWithdrawalComplementaryFields() {
        return '';
      }
    };

    const app = await buildApp({ config, customGateway: brokenGateway });
    const loginRes = await app.inject({
      method: 'POST',
      url: '/api/v1/auth/login',
      payload: { login: 'almoxarife', senha: '123' }
    });
    const token = loginRes.json().token;

    const res = await app.inject({
      method: 'GET',
      url: '/api/v1/movimentos/pendentes',
      headers: { Authorization: `Bearer ${token}` }
    });
    expect(res.statusCode).toBe(502);
    const bodyText = JSON.stringify(res.json());
    expect(bodyText).not.toContain('192.168.7.7');
    expect(bodyText).not.toContain('Corpore');
    expect(bodyText).not.toContain('TMOVCOMPL');

    await app.close();
  });

  it('expõe X-Tecal-Server-Time e serverEpochMs para sincronização automática de relógio', async () => {
    const config = createHardenedConfig();
    const app = await buildApp({
      config,
      customGateway: new MockTotvsRmClient(2)
    });

    const healthRes = await app.inject({
      method: 'GET',
      url: '/api/v1/health'
    });
    expect(healthRes.statusCode).toBe(200);
    expect(Number(healthRes.headers['x-tecal-server-time'])).toBeGreaterThan(0);
    expect(typeof healthRes.json().serverEpochMs).toBe('number');

    await app.close();
  });
});

