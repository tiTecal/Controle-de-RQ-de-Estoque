import { describe, expect, it } from 'vitest';
import { buildApp } from '../src/app.js';
import { AppConfig } from '../src/config/env.js';
import { MockTotvsRmClient } from '../src/rm/mock-totvs-rm-client.js';

function createE2eConfig(): AppConfig {
  return {
    port: 8080,
    timezone: 'America/Sao_Paulo',
    jwtSecret: 'test-e2e-jwt-secret-very-long-and-secure-32chars',
    jwtIssuer: 'tecal-almoxarifado-bff',
    jwtAudience: 'tecal-tablet-kiosk',
    jwtExpirationHours: 24,
    rmMockMode: true,
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
    rmCodColigada: 1,
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
    rmContextoDataServer: 'CODCOLIGADA=1;CODFILIAL=1;CODSISTEMA=T;CODUSUARIO=mestre'
  };
}

describe('API Endpoints (Fastify E2E)', () => {
  it('GET /api/v1/health deve responder 200 OK', async () => {
    const config = createE2eConfig();
    const app = await buildApp({
      config,
      customGateway: new MockTotvsRmClient(1)
    });

    const res = await app.inject({
      method: 'GET',
      url: '/api/v1/health'
    });

    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body.status).toBe('UP');
    expect(body.rmMockMode).toBe(true);
    expect(body.codColigada).toBe(1);
    expect(body.timestampServidor).toBeDefined();

    await app.close();
  });

  it('POST /api/v1/auth/login valida credenciais e retorna JWT', async () => {
    const config = createE2eConfig();
    const app = await buildApp({
      config,
      customGateway: new MockTotvsRmClient(1)
    });

    // Login com senha errada -> 401 INVALID_ESTOQUE_CREDENTIALS
    const failRes = await app.inject({
      method: 'POST',
      url: '/api/v1/auth/login',
      payload: {
        login: 'almoxarife',
        senha: 'senha-errada'
      }
    });
    expect(failRes.statusCode).toBe(401);
    expect(failRes.json().codigo).toBe('INVALID_ESTOQUE_CREDENTIALS');

    // Login correto -> 200 com token
    const successRes = await app.inject({
      method: 'POST',
      url: '/api/v1/auth/login',
      payload: {
        login: 'almoxarife',
        senha: 'tecal123'
      }
    });
    expect(successRes.statusCode).toBe(200);
    const successBody = successRes.json();
    expect(successBody.token).toBeDefined();
    expect(successBody.usuarioEstoque).toBe('almoxarife');

    await app.close();
  });

  it('Rotas protegidas retornam 401 SESSION_EXPIRED sem token', async () => {
    const config = createE2eConfig();
    const app = await buildApp({
      config,
      customGateway: new MockTotvsRmClient(1)
    });

    const res = await app.inject({
      method: 'GET',
      url: '/api/v1/movimentos/pendentes'
    });

    expect(res.statusCode).toBe(401);
    expect(res.json().codigo).toBe('SESSION_EXPIRED');

    await app.close();
  });

  it('Fluxo completo autenticado: pendentes -> itens -> confirmação com HTTP 403 em erro e 200 em sucesso', async () => {
    const config = createE2eConfig();
    const app = await buildApp({
      config,
      customGateway: new MockTotvsRmClient(1)
    });

    // 1. Login Estoque
    const loginRes = await app.inject({
      method: 'POST',
      url: '/api/v1/auth/login',
      payload: {
        login: 'almoxarife',
        senha: 'tecal123'
      }
    });
    const token = loginRes.json().token;
    const authHeaders = { Authorization: `Bearer ${token}` };

    // 2. Listar pendentes
    const pendentesRes = await app.inject({
      method: 'GET',
      url: '/api/v1/movimentos/pendentes',
      headers: authHeaders
    });
    expect(pendentesRes.statusCode).toBe(200);
    const pendentes = pendentesRes.json();
    expect(pendentes.length).toBeGreaterThan(0);
    const idMovAlvo = pendentes[0].idMov;

    // 3. Listar itens
    const itensRes = await app.inject({
      method: 'GET',
      url: `/api/v1/movimentos/${idMovAlvo}/itens`,
      headers: authHeaders
    });
    expect(itensRes.statusCode).toBe(200);
    expect(itensRes.json().length).toBeGreaterThan(0);

    // 4. Modal de confirmação: senha errada do retirante -> HTTP 403 INVALID_WITHDRAWER_CREDENTIALS
    const failConfirmRes = await app.inject({
      method: 'POST',
      url: `/api/v1/movimentos/${idMovAlvo}/baixar`,
      headers: authHeaders,
      payload: {
        login: 'carlos.silva',
        senha: 'senha-errada-retirante',
        confirmado100Porcento: true,
        idempotencyKey: 'idemp-fail-1'
      }
    });
    expect(failConfirmRes.statusCode).toBe(403);
    expect(failConfirmRes.json().codigo).toBe('INVALID_WITHDRAWER_CREDENTIALS');

    // 5. Confirmação com sucesso -> HTTP 200 OK
    const successRetiradaRes = await app.inject({
      method: 'POST',
      url: `/api/v1/movimentos/${idMovAlvo}/baixar`,
      headers: authHeaders,
      payload: {
        login: 'carlos.silva',
        senha: 'tecal123',
        confirmado100Porcento: true,
        idempotencyKey: 'idemp-success-1'
      }
    });
    expect(successRetiradaRes.statusCode).toBe(200);
    const resBody = successRetiradaRes.json();
    expect(resBody.retiradoPor).toBe('carlos.silva');
    expect(resBody.idMov).toBe(idMovAlvo);

    // 6. Tentar baixar novamente o mesmo movimento sem idempotencyKey -> 409 ALREADY_WITHDRAWN
    const repeatRes = await app.inject({
      method: 'POST',
      url: `/api/v1/movimentos/${idMovAlvo}/baixar`,
      headers: authHeaders,
      payload: {
        login: 'outro.colaborador',
        senha: 'tecal123',
        confirmado100Porcento: true,
        idempotencyKey: 'outra-chave'
      }
    });
    expect(repeatRes.statusCode).toBe(409);
    expect(repeatRes.json().codigo).toBe('ALREADY_WITHDRAWN');

    await app.close();
  });

  it('Endpoints de Gestão de Usuários (/api/v1/usuarios) cadastram, listam e inativam usuários na base ALMOXARIFADO', async () => {
    const config = createE2eConfig();
    const app = await buildApp({
      config,
      customGateway: new MockTotvsRmClient(1)
    });

    const loginRes = await app.inject({
      method: 'POST',
      url: '/api/v1/auth/login',
      payload: {
        login: 'almoxarife',
        senha: 'tecal123'
      }
    });
    const token = loginRes.json().token;
    const authHeaders = { Authorization: `Bearer ${token}` };

    // 1. Cadastra novo usuário retirante
    const createRes = await app.inject({
      method: 'POST',
      url: '/api/v1/usuarios',
      headers: authHeaders,
      payload: {
        login: 'pedro.santos',
        nomeCompleto: 'Pedro Santos (Eletricista)',
        senha: 'SenhaForte#2026',
        perfil: 'RETIRANTE',
        ativo: true
      }
    });
    expect(createRes.statusCode).toBe(200);
    expect(createRes.json().login).toBe('pedro.santos');
    expect(createRes.json().perfil).toBe('RETIRANTE');

    // 2. Lista usuários
    const listRes = await app.inject({
      method: 'GET',
      url: '/api/v1/usuarios',
      headers: authHeaders
    });
    expect(listRes.statusCode).toBe(200);
    expect(listRes.json().some((u: { login: string }) => u.login === 'pedro.santos')).toBe(true);

    // 3. Inativa o usuário e confirma que ele não consegue mais dar baixa
    const disableRes = await app.inject({
      method: 'PATCH',
      url: '/api/v1/usuarios/pedro.santos/status',
      headers: authHeaders,
      payload: { ativo: false }
    });
    expect(disableRes.statusCode).toBe(200);
    expect(disableRes.json().ativo).toBe(false);

    const pendentes = (
      await app.inject({
        method: 'GET',
        url: '/api/v1/movimentos/pendentes',
        headers: authHeaders
      })
    ).json();

    const failWithDisabledRes = await app.inject({
      method: 'POST',
      url: `/api/v1/movimentos/${pendentes[0].idMov}/baixar`,
      headers: authHeaders,
      payload: {
        login: 'pedro.santos',
        senha: 'SenhaForte#2026',
        confirmado100Porcento: true,
        idempotencyKey: 'idemp-disabled-user'
      }
    });
    expect(failWithDisabledRes.statusCode).toBe(403);

    await app.close();
  });
});
