import { describe, expect, it, vi } from 'vitest';
import {
  buildEmployeeCandidateLoginsMap,
  resolveRmHttpBaseUrl,
  RmHostAuthClient
} from '../src/rm/rm-auth-helpers.js';

describe('resolveRmHttpBaseUrl', () => {
  it('substitui porta SQL 1433 pela porta HTTP 8051 do RM Host', () => {
    expect(resolveRmHttpBaseUrl('http://192.168.7.7:1433', '192.168.7.7')).toBe(
      'http://192.168.7.7:8051'
    );
  });

  it('usa dbHost:8051 quando RM_BASE_URL estiver vazio', () => {
    expect(resolveRmHttpBaseUrl('', '192.168.7.7')).toBe('http://192.168.7.7:8051');
  });

  it('preserva URL explícita do RM Host em outra porta ou HTTPS', () => {
    expect(resolveRmHttpBaseUrl('https://rm.tecal.local:8443/', '192.168.7.7')).toBe(
      'https://rm.tecal.local:8443'
    );
  });
});

describe('buildEmployeeCandidateLoginsMap', () => {
  it('associa chapa transferida, nome truncado/fonético e contas 2-CHAPA sem confundir homônimos', () => {
    const employees = [
      {
        key: 'emp-raphael',
        chapa: '001162',
        nomeCompleto: 'RAPHAEL ARAUJO BATISTA',
        fullCpf: '11122233344',
        loginPpessoa: '001162',
        emailPessoa: 'raphael.araujo@tecal.com.br'
      },
      {
        key: 'emp-guilherme',
        chapa: '001150',
        nomeCompleto: 'GUILHERME ALVES PASSOS SOUSA ANDRADE',
        fullCpf: '22233344455',
        loginPpessoa: '22233344455',
        emailPessoa: ''
      },
      {
        key: 'emp-joao-1',
        chapa: '000101',
        nomeCompleto: 'JOAO SILVA',
        fullCpf: '33344455566',
        loginPpessoa: '33344455566',
        emailPessoa: 'joao1@tecal.com.br'
      },
      {
        key: 'emp-joao-2',
        chapa: '000102',
        nomeCompleto: 'JOAO SILVA',
        fullCpf: '44455566677',
        loginPpessoa: '44455566677',
        emailPessoa: ''
      },
      {
        key: 'emp-maria',
        chapa: '000555',
        nomeCompleto: 'MARIA OLIVEIRA',
        fullCpf: '55566677788',
        loginPpessoa: '000555',
        emailPessoa: ''
      }
    ];

    const gusuarios = [
      {
        codUsuario: 'raphael.araujo',
        nome: 'Raphael Araujo Batista 2-001123',
        email: ''
      },
      {
        codUsuario: '2-001162',
        nome: 'RAPHAEL ARAUJO BATISTA',
        email: ''
      },
      {
        codUsuario: 'guilherme.alves',
        nome: 'Guilherme Alves Passos Souza A',
        email: ''
      },
      {
        codUsuario: 'joao.silva.ambiguous',
        nome: 'Joao Silva',
        email: ''
      },
      {
        codUsuario: 'carlos.recycled',
        nome: 'Carlos Pereira 2-000555',
        email: ''
      }
    ];

    const { loginsByEmployeeKey, primaryNamedByEmployeeKey } = buildEmployeeCandidateLoginsMap(
      employees,
      gusuarios
    );

    expect(primaryNamedByEmployeeKey.get('emp-raphael')).toBe('raphael.araujo');
    expect(loginsByEmployeeKey.get('emp-raphael')).toEqual(
      expect.arrayContaining(['raphael.araujo', '001162', '11122233344', '2-001162'])
    );

    expect(primaryNamedByEmployeeKey.get('emp-guilherme')).toBe('guilherme.alves');
    expect(loginsByEmployeeKey.get('emp-guilherme')).toEqual(
      expect.arrayContaining(['guilherme.alves', '22233344455', '001150'])
    );

    // Homônimos sem tag ou email único não devem receber o login ambíguo
    expect(primaryNamedByEmployeeKey.get('emp-joao-1')).toBeUndefined();
    expect(primaryNamedByEmployeeKey.get('emp-joao-2')).toBeUndefined();
    expect(loginsByEmployeeKey.get('emp-joao-1')).not.toContain('joao.silva.ambiguous');
    expect(loginsByEmployeeKey.get('emp-joao-2')).not.toContain('joao.silva.ambiguous');

    // Chapa reciclada com primeiro nome divergente não deve cruzar pessoas
    expect(primaryNamedByEmployeeKey.get('emp-maria')).toBeUndefined();
    expect(loginsByEmployeeKey.get('emp-maria')).not.toContain('carlos.recycled');
  });
});

describe('RmHostAuthClient', () => {
  it('valida via REST /api/connect/token sem tocar no endpoint SOAP quando retorna 200', async () => {
    const fetchMock = vi.fn(async () => {
      return new Response(JSON.stringify({ AccessToken: 'jwt-ok' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' }
      });
    });

    const client = new RmHostAuthClient('http://192.168.7.7:8051', fetchMock as unknown as typeof fetch);
    const ok = await client.verifyCredential('2-000512', 'SenhaSegura@1');

    expect(ok).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0]?.[0]).toBe('http://192.168.7.7:8051/api/connect/token');
  });

  it('não chama SOAP IwsBase após rejeição FE016 no REST para não dobrar NUMLOGININVALIDO', async () => {
    const fetchMock = vi.fn(async () => {
      return new Response(JSON.stringify({ code: 'FE016', message: 'Usuário ou Senha inválidos!' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' }
      });
    });

    const client = new RmHostAuthClient('http://192.168.7.7:8051', fetchMock as unknown as typeof fetch);
    const first = await client.verifyCredential('usuario.teste', 'SenhaErrada1');
    // Mesmo par repetido em sequência deve ser bloqueado localmente pelo cooldown anti-bloqueio
    const repeatSamePair = await client.verifyCredential('usuario.teste', 'SenhaErrada1');

    expect(first).toBe(false);
    expect(repeatSamePair).toBe(false);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('faz fallback para SOAP IwsBase/AutenticaAcesso somente se /api/connect/token estiver indisponível', async () => {
    const fetchMock = vi
      .fn()
      .mockRejectedValueOnce(new Error('connect ECONNREFUSED'))
      .mockResolvedValueOnce(
        new Response(
          '<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><AutenticaAcessoResponse xmlns="http://www.totvs.com/"><AutenticaAcessoResult>true</AutenticaAcessoResult></AutenticaAcessoResponse></s:Body></s:Envelope>',
          { status: 200, headers: { 'Content-Type': 'text/xml' } }
        )
      );

    const client = new RmHostAuthClient('http://192.168.7.7:8051', fetchMock as unknown as typeof fetch);
    const ok = await client.verifyCredential('2-000512', 'SenhaLegada');

    expect(ok).toBe(true);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1]?.[0]).toBe('http://192.168.7.7:8051/wsDataServer/IwsBase');
    const headers = fetchMock.mock.calls[1]?.[1]?.headers as Record<string, string>;
    expect(headers.SOAPAction).toBe('"http://www.totvs.com/IwsBase/AutenticaAcesso"');
  });

  it('limita falhas remotas a no máximo 3 tentativas distintas por usuário na janela de 5 minutos', async () => {
    const fetchMock = vi.fn(async () => {
      return new Response(JSON.stringify({ code: 'FE016' }), { status: 400 });
    });

    const client = new RmHostAuthClient('http://192.168.7.7:8051', fetchMock as unknown as typeof fetch);
    await expect(client.verifyCredential('alguem', 'errada-1')).resolves.toBe(false);
    await expect(client.verifyCredential('alguem', 'errada-2')).resolves.toBe(false);
    await expect(client.verifyCredential('alguem', 'errada-3')).resolves.toBe(false);
    await expect(client.verifyCredential('alguem', 'errada-4')).resolves.toBe(false);

    expect(fetchMock).toHaveBeenCalledTimes(3);
  });
});

