import { FastifyInstance, FastifyReply, FastifyRequest } from 'fastify';
import { DateTime } from 'luxon';
import { z } from 'zod';
import { AppConfig } from '../config/env.js';
import { BusinessValidationError, InvalidRmCredentialsError } from '../domain/errors.js';
import {
  ConfirmWithdrawalRequest,
  EstoqueLoginRequest,
  HealthResponse,
  LookupCpfRequest,
  UpdateUserStatusRequest,
  UpsertUserRequest,
  VerifyAdminPinRequest,
  VerifyAdminPinResponse
} from '../domain/types.js';
import {
  DEFAULT_APP_SIGNING_SECRET,
  hashAdminPin,
  InMemoryRateLimiter,
  onlyDigits,
  safeEqualStrings,
  verifyClientHmacSignature
} from '../security/security.js';
import { WithdrawalService } from '../services/withdrawal.service.js';

interface RouteOptions {
  config: AppConfig;
  withdrawalService: WithdrawalService;
}

const LoginBodySchema = z.object({
  login: z.string().trim().min(1).max(80),
  senha: z.string().min(1).max(128)
});

const ConfirmWithdrawalSchema = z.object({
  login: z.string().trim().min(1).max(80),
  senha: z.string().min(1).max(128),
  confirmado100Porcento: z.boolean(),
  idempotencyKey: z.string().trim().max(120).optional().default('')
});

const LookupCpfSchema = z.object({
  cpf: z.string().trim().min(11).max(20)
});

const VerifyAdminPinSchema = z.object({
  pin: z.string().trim().min(4).max(32)
});

const UpsertUserSchema = z.object({
  login: z.string().trim().min(2).max(60),
  nomeCompleto: z.string().trim().min(2).max(120),
  senha: z.string().min(1).max(128).optional(),
  perfil: z.enum(['ESTOQUE', 'RETIRANTE', 'AMBOS']).optional().default('RETIRANTE'),
  ativo: z.boolean().optional().default(true)
});

const UpdateStatusSchema = z.object({
  ativo: z.boolean()
});

function parseZod<T>(schema: z.ZodType<T>, raw: unknown): T {
  const parsed = schema.safeParse(raw);
  if (!parsed.success) {
    throw new BusinessValidationError('Parâmetros de entrada inválidos ou fora do formato esperado.');
  }
  return parsed.data;
}

export async function registerWithdrawalRoutes(
  fastify: FastifyInstance,
  { config, withdrawalService }: RouteOptions
): Promise<void> {
  const usedNonces = new Map<string, number>();
  const loginLimiter = new InMemoryRateLimiter();
  const withdrawerLimiter = new InMemoryRateLimiter();
  const cpfLookupLimiter = new InMemoryRateLimiter();
  const adminPinLimiter = new InMemoryRateLimiter();

  const enforceSignature = Boolean(config.enforceAppSignature);
  const appSigningSecret = config.appSigningSecret || DEFAULT_APP_SIGNING_SECRET;
  const requireAdminAuth = Boolean(config.requireAdminAuth);
  const expectedPinHash =
    config.adminPinSha256 ||
    'd64d6aa9de9d8e1cafc04bdf82ff232ea010a90f88f64a16f837176b9675ef8f';

  const verifyAppSignatureHook = async (req: FastifyRequest, reply: FastifyReply) => {
    if (!enforceSignature) return;
    const check = verifyClientHmacSignature({
      secret: appSigningSecret,
      method: req.method,
      path: req.url,
      timestampHeader: req.headers['x-tecal-timestamp'] as string | undefined,
      nonceHeader: req.headers['x-tecal-nonce'] as string | undefined,
      signatureHeader: req.headers['x-tecal-signature'] as string | undefined,
      usedNonces
    });

    if (!check.ok) {
      return reply.status(403).send({
        codigo: 'UNAUTHORIZED_CLIENT',
        mensagem: check.reason || 'Cliente não autorizado a consultar a API corporativa.'
      });
    }
  };

  const verifyAdminPrivilege = async (req: FastifyRequest, reply: FastifyReply) => {
    if (!requireAdminAuth) return;
    const adminToken = String(req.headers['x-tecal-admin-token'] || '').trim();
    if (!adminToken) {
      return reply.status(403).send({
        codigo: 'ADMIN_AUTH_REQUIRED',
        mensagem: 'Autenticação administrativa necessária para alterar usuários.'
      });
    }
    try {
      const decoded = fastify.jwt.verify<{ role?: string; aud?: string }>(adminToken, {
        allowedIss: config.jwtIssuer,
        allowedAud: `${config.jwtAudience}:admin`
      });
      if (decoded?.role !== 'TECAL_ADMIN') {
        throw new Error('Perfil inválido');
      }
    } catch {
      return reply.status(403).send({
        codigo: 'ADMIN_AUTH_INVALID',
        mensagem: 'Sessão administrativa expirada ou inválida. Informe o PIN administrativo novamente.'
      });
    }
  };

  // Prefixo global da API
  fastify.register(
    async (api) => {
      // Healthcheck público mínimo (sem expor estrutura interna de servidor/banco)
      api.get('/health', async (_req, reply) => {
        const response: HealthResponse = {
          status: 'UP',
          rmMockMode: config.rmMockMode,
          codColigada: config.rmCodColigada,
          timestampServidor: DateTime.now().setZone(config.timezone).toISO() || '',
          serverEpochMs: Date.now()
        };
        return reply.status(200).send(response);
      });

      // Verificação de PIN administrativo protegida contra força bruta + assinatura do app
      api.post<{ Body: VerifyAdminPinRequest }>(
        '/admin/verify-pin',
        { onRequest: verifyAppSignatureHook },
        async (req, reply) => {
          const clientKey = `pin:${req.ip}`;
          const lockState = adminPinLimiter.isBlocked(clientKey, 5);
          if (lockState.blocked) {
            return reply.status(429).send({
              codigo: 'RATE_LIMITED',
              mensagem: `Muitas tentativas inválidas de PIN. Aguarde ${lockState.retryAfterSeconds}s.`
            });
          }

          const body = parseZod(VerifyAdminPinSchema, req.body || {});
          const providedHash = hashAdminPin(body.pin);
          const isValid = safeEqualStrings(providedHash, expectedPinHash);

          if (!isValid) {
            adminPinLimiter.recordFailure(clientKey, 15 * 60_000);
            return reply.status(403).send({
              codigo: 'INVALID_ADMIN_PIN',
              mensagem: 'PIN administrativo incorreto.'
            });
          }

          adminPinLimiter.reset(clientKey);
          const adminToken = fastify.jwt.sign(
            {
              sub: 'tecal-admin',
              role: 'TECAL_ADMIN',
              iss: config.jwtIssuer,
              aud: `${config.jwtAudience}:admin`
            },
            { expiresIn: '10m' }
          );

          const res: VerifyAdminPinResponse = {
            valid: true,
            adminToken,
            expiraEmMinutos: 10
          };
          return reply.status(200).send(res);
        }
      );

      // Login inicial do estoque (protegido contra força bruta + assinatura do app)
      api.post<{ Body: EstoqueLoginRequest }>(
        '/auth/login',
        { onRequest: verifyAppSignatureHook },
        async (req, reply) => {
          const body = parseZod(LoginBodySchema, req.body || {});
          const rateKey = `login:${req.ip}:${body.login.toLowerCase()}`;
          const lockState = loginLimiter.isBlocked(rateKey, 8);
          if (lockState.blocked) {
            return reply.status(429).send({
              codigo: 'RATE_LIMITED',
              mensagem: `Muitas tentativas de login. Aguarde ${lockState.retryAfterSeconds}s.`
            });
          }

          try {
            const result = await withdrawalService.loginSessaoEstoque(body, (payload) => {
              return fastify.jwt.sign(payload, {
                expiresIn: `${config.jwtExpirationHours}h`
              });
            });
            loginLimiter.reset(rateKey);
            return reply.status(200).send(result);
          } catch (err) {
            if (err instanceof InvalidRmCredentialsError) {
              loginLimiter.recordFailure(rateKey, 10 * 60_000);
            }
            throw err;
          }
        }
      );

      // Rotas protegidas pela sessão ativa do tablet na porta do almoxarifado
      api.register(async (protectedRoutes) => {
        // 1. Verifica assinatura HMAC do aplicativo (anti-engenharia reversa / curl)
        protectedRoutes.addHook('onRequest', verifyAppSignatureHook);

        // 2. Verifica JWT exclusivo da sessão do estoque (rejeita tokens de outros públicos/audiências)
        protectedRoutes.addHook('onRequest', async (req, reply) => {
          try {
            const payload = await req.jwtVerify<{ role?: string }>({
              allowedIss: config.jwtIssuer,
              allowedAud: config.jwtAudience
            });
            if (payload?.role === 'TECAL_ADMIN') {
              throw new Error('Token admin não pode atuar como sessão de estoque');
            }
          } catch {
            return reply.status(401).send({
              codigo: 'SESSION_EXPIRED',
              mensagem:
                'Sessão do estoque expirada ou ausente. Faça login do usuário do estoque.'
            });
          }
        });

        // Lista movimentos 1.1.10 sem retirado_por, data_retirada e hora_retirada preenchidos
        protectedRoutes.get('/movimentos/pendentes', async (_req, reply) => {
          const pendentes = await withdrawalService.listarMovimentos1110Pendentes();
          return reply.status(200).send(pendentes);
        });

        // Lista 100% dos itens vinculados ao movimento 1.1.10 indicado pelo estoquista
        protectedRoutes.get(
          '/movimentos/:idMov/itens',
          async (req: FastifyRequest<{ Params: { idMov: string } }>, reply) => {
            const idMov = parseInt(req.params.idMov, 10);
            if (isNaN(idMov) || idMov <= 0) {
              throw new BusinessValidationError('Parâmetro idMov inválido.');
            }
            const itens = await withdrawalService.listarItensMovimento1110(idMov);
            return reply.status(200).send(itens);
          }
        );

        // Dá baixa na retirada 100% do movimento 1.1.10 exigindo login/ref e senha do colaborador retirante
        protectedRoutes.post(
          '/movimentos/:idMov/baixar',
          async (
            req: FastifyRequest<{
              Params: { idMov: string };
              Body: ConfirmWithdrawalRequest;
            }>,
            reply
          ) => {
            const idMov = parseInt(req.params.idMov, 10);
            if (isNaN(idMov) || idMov <= 0) {
              throw new BusinessValidationError('Parâmetro idMov inválido.');
            }

            const body = parseZod(ConfirmWithdrawalSchema, req.body || {});
            const rateKey = `withdraw:${req.ip}:${body.login.toLowerCase()}`;
            const lockState = withdrawerLimiter.isBlocked(rateKey, 6);
            if (lockState.blocked) {
              return reply.status(429).send({
                codigo: 'RATE_LIMITED',
                mensagem: `Muitas tentativas de senha incorreta para este colaborador. Aguarde ${lockState.retryAfterSeconds}s.`
              });
            }

            try {
              const res = await withdrawalService.confirmarBaixaRetirada1110(idMov, {
                login: body.login,
                senha: body.senha,
                confirmado100Porcento: body.confirmado100Porcento,
                idempotencyKey: body.idempotencyKey || ''
              });
              withdrawerLimiter.reset(rateKey);
              return reply.status(200).send(res);
            } catch (err) {
              if (err instanceof InvalidRmCredentialsError) {
                withdrawerLimiter.recordFailure(rateKey, 5 * 60_000);
              }
              throw err;
            }
          }
        );

        // Lista colaboradores ativos já com CPF mascarado ("158.***.***-**") e referências opacas
        protectedRoutes.get('/usuarios', async (_req, reply) => {
          const usuarios = await withdrawalService.listarUsuarios();
          return reply.status(200).send(usuarios);
        });

        // Busca exata por CPF completo (11 dígitos) feita apenas na RAM do servidor (zero vazamento de lista completa)
        protectedRoutes.post(
          '/usuarios/lookup-cpf',
          async (req: FastifyRequest<{ Body: LookupCpfRequest }>, reply) => {
            const check = cpfLookupLimiter.checkAndIncrement(`cpf:${req.ip}`, 10, 60_000);
            if (!check.allowed) {
              return reply.status(429).send({
                codigo: 'RATE_LIMITED',
                mensagem: `Limite de consultas excedido. Aguarde ${check.retryAfterSeconds}s.`
              });
            }

            const body = parseZod(LookupCpfSchema, req.body || {});
            const digits = onlyDigits(body.cpf);
            if (digits.length !== 11) {
              throw new BusinessValidationError('Informe os 11 dígitos do CPF para consulta exata.');
            }
            const matches = await withdrawalService.buscarColaboradorPorCpfExato(digits);
            return reply.status(200).send(matches);
          }
        );

        // =========================================================================
        // Rotas Administrativas Protegidas (Exigem X-Tecal-Admin-Token válido)
        // =========================================================================

        protectedRoutes.post<{ Body: UpsertUserRequest }>(
          '/usuarios',
          { onRequest: verifyAdminPrivilege },
          async (req, reply) => {
            const body = parseZod(UpsertUserSchema, req.body || {});
            const saved = await withdrawalService.salvarUsuario(body);
            return reply.status(200).send(saved);
          }
        );

        protectedRoutes.patch<{
          Params: { login: string };
          Body: UpdateUserStatusRequest;
        }>(
          '/usuarios/:login/status',
          { onRequest: verifyAdminPrivilege },
          async (req, reply) => {
            const body = parseZod(UpdateStatusSchema, req.body || {});
            const updated = await withdrawalService.alterarStatusUsuario(
              req.params.login,
              body.ativo
            );
            return reply.status(200).send(updated);
          }
        );
      });
    },
    { prefix: '/api/v1' }
  );
}
