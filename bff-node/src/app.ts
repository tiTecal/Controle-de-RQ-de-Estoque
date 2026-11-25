import fastifyCors from '@fastify/cors';
import fastifyJwt from '@fastify/jwt';
import fastify, { FastifyInstance } from 'fastify';
import { AppConfig } from './config/env.js';
import {
  BusinessValidationError,
  InvalidRmCredentialsError,
  MovementAlreadyWithdrawnError,
  MovementNotFoundError,
  TotvsRmIntegrationError
} from './domain/errors.js';
import { MockTotvsRmClient } from './rm/mock-totvs-rm-client.js';
import { SqlServerRmClient } from './rm/sqlserver-rm-client.js';
import { TotvsRmGateway } from './rm/totvs-rm-gateway.interface.js';
import { registerWithdrawalRoutes } from './routes/withdrawal.routes.js';
import { WithdrawalService } from './services/withdrawal.service.js';

export interface BuildAppOptions {
  config: AppConfig;
  customGateway?: TotvsRmGateway;
}

export async function buildApp({
  config,
  customGateway
}: BuildAppOptions): Promise<FastifyInstance> {
  const isTest = process.env.NODE_ENV === 'test' || process.env.VITEST === 'true';
  const app = fastify({
    trustProxy: true, // Permite identificar o IP real via X-Forwarded-For quando atrás do IIS ARR
    bodyLimit: 64 * 1024, // 64 KB máximo por payload para conter abuso de memória
    logger: isTest
      ? false
      : {
          level: process.env.LOG_LEVEL || 'info',
          redact: {
            paths: [
              'req.headers.authorization',
              'req.headers["x-tecal-signature"]',
              'req.headers["x-tecal-admin-token"]',
              'body.senha',
              'body.cpf',
              'body.pin'
            ],
            censor: '[REDACTED]'
          }
        }
  });

  // Cabeçalhos de segurança defensivos (Anti-Cache / Anti-Sniffing / Anti-Framing)
  app.addHook('onSend', async (_req, reply, payload) => {
    reply.header('X-Content-Type-Options', 'nosniff');
    reply.header('X-Frame-Options', 'DENY');
    reply.header('Cache-Control', 'no-store, no-cache, must-revalidate, private');
    reply.header('Pragma', 'no-cache');
    reply.header('Referrer-Policy', 'no-referrer');
    reply.header('X-Tecal-Server-Time', String(Date.now()));
    reply.removeHeader('X-Powered-By');
    return payload;
  });

  // CORS restritivo: bloqueia chamadas cross-origin arbitrárias de navegadores (apps nativos não dependem de CORS)
  await app.register(fastifyCors, {
    origin: config.corsAllowedOrigin ?? false,
    methods: ['GET', 'POST', 'PATCH', 'OPTIONS'],
    allowedHeaders: [
      'Authorization',
      'Content-Type',
      'X-Tecal-Timestamp',
      'X-Tecal-Nonce',
      'X-Tecal-Signature',
      'X-Tecal-Admin-Token'
    ]
  });

  // JWT
  await app.register(fastifyJwt, {
    secret: config.jwtSecret,
    sign: {
      iss: config.jwtIssuer,
      aud: config.jwtAudience
    },
    verify: {
      allowedIss: config.jwtIssuer
    }
  });

  // Gateway TOTVS RM: Modo Simulador (RM_MOCK_MODE=true) ou Conexão Direta ao SQL Server
  const rmGateway: TotvsRmGateway =
    customGateway ??
    (config.rmMockMode
      ? new MockTotvsRmClient(config.rmCodColigada)
      : new SqlServerRmClient(config));

  app.addHook('onClose', async () => {
    if (rmGateway.close) {
      await rmGateway.close();
    }
  });

  const withdrawalService = new WithdrawalService(config, rmGateway);

  // Tratamento centralizado de erros (sanitizado: nunca expõe IPs, schemas ou detalhes de banco)
  app.setErrorHandler((error, request, reply) => {
    if (error instanceof InvalidRmCredentialsError) {
      if (error.isWithdrawalConfirmation) {
        return reply.status(403).send({
          codigo: 'INVALID_WITHDRAWER_CREDENTIALS',
          mensagem: error.message || 'Login ou senha do retirante inválidos.'
        });
      }
      return reply.status(401).send({
        codigo: 'INVALID_ESTOQUE_CREDENTIALS',
        mensagem: error.message || 'Login ou senha do estoque inválidos.'
      });
    }

    if (error instanceof MovementAlreadyWithdrawnError) {
      return reply.status(409).send({
        codigo: 'ALREADY_WITHDRAWN',
        mensagem:
          error.message || 'Este movimento 1.1.10 já teve sua retirada registrada.'
      });
    }

    if (error instanceof MovementNotFoundError) {
      return reply.status(404).send({
        codigo: 'MOVEMENT_NOT_FOUND',
        mensagem: error.message || 'Movimento 1.1.10 não encontrado.'
      });
    }

    if (error instanceof BusinessValidationError) {
      return reply.status(400).send({
        codigo: 'VALIDATION_ERROR',
        mensagem: error.message || 'Dados inválidos na requisição.'
      });
    }

    if (error instanceof TotvsRmIntegrationError) {
      request.log.error(error, 'Erro de integração com o banco de dados ERP');
      return reply.status(502).send({
        codigo: 'RM_INTEGRATION_ERROR',
        mensagem: 'Falha temporária de comunicação com o servidor ERP. Tente novamente.'
      });
    }

    // Erros de JWT nativos do Fastify
    if (
      (error as { statusCode?: number }).statusCode === 401 ||
      (error as { code?: string }).code === 'FST_JWT_NO_AUTHORIZATION_IN_HEADER' ||
      (error as { code?: string }).code === 'FST_JWT_AUTHORIZATION_TOKEN_EXPIRED'
    ) {
      return reply.status(401).send({
        codigo: 'SESSION_EXPIRED',
        mensagem: 'Sessão do estoque expirada ou ausente. Faça login do usuário do estoque.'
      });
    }

    request.log.error(error, 'Erro interno inesperado no BFF');
    return reply.status(500).send({
      codigo: 'INTERNAL_ERROR',
      mensagem: 'Erro interno no servidor de integração.'
    });
  });

  // Registra as rotas
  await registerWithdrawalRoutes(app, { config, withdrawalService });

  return app;
}
