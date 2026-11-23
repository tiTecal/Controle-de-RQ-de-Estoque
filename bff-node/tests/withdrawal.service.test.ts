import { describe, expect, it } from 'vitest';
import { AppConfig } from '../src/config/env.js';
import {
  BusinessValidationError,
  InvalidRmCredentialsError
} from '../src/domain/errors.js';
import { MockTotvsRmClient } from '../src/rm/mock-totvs-rm-client.js';
import { PasswordHasher } from '../src/rm/password-hasher.js';
import { WithdrawalService } from '../src/services/withdrawal.service.js';

function createTestConfig(): AppConfig {
  return {
    port: 8080,
    timezone: 'America/Sao_Paulo',
    jwtSecret: 'test-secret',
    jwtIssuer: 'test-issuer',
    jwtAudience: 'test-audience',
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

describe('WithdrawalService & MockTotvsRmClient', () => {
  it('fluxo completo de listagem e baixa 100 por cento remove movimento 1.1.10 da lista pendente', async () => {
    const config = createTestConfig();
    const service = new WithdrawalService(
      config,
      new MockTotvsRmClient(1),
      () => 'mock-jwt-token'
    );

    const initialList = await service.listarMovimentos1110Pendentes();
    expect(initialList).toHaveLength(3);
    const targetIdMov = initialList[0].idMov;

    // Recusa se confirmado100Porcento for falso
    await expect(
      service.confirmarBaixaRetirada1110(targetIdMov, {
        login: 'joao.souza',
        senha: 'tecal123',
        confirmado100Porcento: false,
        idempotencyKey: 'key-fail'
      })
    ).rejects.toThrow(BusinessValidationError);

    // Recusa senha incorreta com isWithdrawalConfirmation = true (sem derrubar sessão do estoque)
    try {
      await service.confirmarBaixaRetirada1110(targetIdMov, {
        login: 'joao.souza',
        senha: 'senha-errada',
        confirmado100Porcento: true,
        idempotencyKey: 'key-wrong-pass'
      });
      expect.unreachable('Deveria ter lançado InvalidRmCredentialsError');
    } catch (err) {
      expect(err).toBeInstanceOf(InvalidRmCredentialsError);
      expect((err as InvalidRmCredentialsError).isWithdrawalConfirmation).toBe(true);
    }

    // Confirma com sucesso e remove da fila pendente
    const confirmation = await service.confirmarBaixaRetirada1110(targetIdMov, {
      login: 'joao.souza',
      senha: 'tecal123',
      confirmado100Porcento: true,
      idempotencyKey: 'uuid-retirada-001'
    });

    expect(confirmation.retiradoPor).toBe('joao.souza');
    expect(confirmation.idMov).toBe(targetIdMov);

    const updatedList = await service.listarMovimentos1110Pendentes();
    expect(updatedList).toHaveLength(2);
    expect(updatedList.some((m) => m.idMov === targetIdMov)).toBe(false);

    // Reenvio com mesma idempotencyKey retorna exatamente a mesma resposta sem erro 409
    const replay = await service.confirmarBaixaRetirada1110(targetIdMov, {
      login: 'joao.souza',
      senha: 'tecal123',
      confirmado100Porcento: true,
      idempotencyKey: 'uuid-retirada-001'
    });

    expect(replay).toEqual(confirmation);
  });

  it('PasswordHasher valida corretamente hashes SHA-256 + Salt (UTF-16LE e UTF-8)', () => {
    const salt = PasswordHasher.generateSalt();
    expect(salt).toHaveLength(32);

    const hash = PasswordHasher.hashPassword('Tecal@77', salt);
    expect(hash).toHaveLength(64);
    expect(PasswordHasher.verifyPassword('Tecal@77', salt, hash)).toBe(true);
    expect(PasswordHasher.verifyPassword('SenhaErrada', salt, hash)).toBe(false);
  });

  it('respeita segregação de perfil ESTOQUE vs RETIRANTE e status ATIVO na base ALMOXARIFADO', async () => {
    const config = createTestConfig();
    const service = new WithdrawalService(
      config,
      new MockTotvsRmClient(1),
      () => 'mock-jwt-token'
    );

    // carlos.silva possui perfil RETIRANTE -> não pode abrir sessão do estoque no tablet
    await expect(
      service.loginSessaoEstoque({ login: 'carlos.silva', senha: 'tecal123' })
    ).rejects.toThrow(InvalidRmCredentialsError);

    // almoxarife possui perfil AMBOS -> pode abrir sessão do estoque
    const loginRes = await service.loginSessaoEstoque({
      login: 'almoxarife',
      senha: 'tecal123'
    });
    expect(loginRes.usuarioEstoque).toBe('almoxarife');
  });
});
