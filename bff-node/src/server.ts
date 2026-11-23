import { buildApp } from './app.js';
import { loadAppConfig } from './config/env.js';

async function start() {
  const config = loadAppConfig();

  const app = await buildApp({ config });

  // Encerramento limpo (NSSM envia Ctrl+C/SIGINT ao parar ou reiniciar o serviço):
  // fecha o servidor HTTP e o pool do SQL Server antes de sair.
  let shuttingDown = false;
  const shutdown = async (signal: string, exitCode = 0) => {
    if (shuttingDown) return;
    shuttingDown = true;
    app.log.info(`Sinal ${signal} recebido. Encerrando BFF...`);
    const forceExit = setTimeout(() => process.exit(exitCode || 1), 10_000);
    forceExit.unref();
    try {
      await app.close();
    } catch (err) {
      app.log.error(err, 'Falha ao encerrar o BFF graciosamente');
      exitCode = 1;
    }
    process.exit(exitCode);
  };

  process.on('SIGINT', () => void shutdown('SIGINT'));
  process.on('SIGTERM', () => void shutdown('SIGTERM'));
  process.on('SIGBREAK', () => void shutdown('SIGBREAK'));
  process.on('uncaughtException', (err) => {
    app.log.error(err, 'uncaughtException');
    void shutdown('uncaughtException', 1);
  });
  process.on('unhandledRejection', (reason) => {
    app.log.error({ reason }, 'unhandledRejection');
  });

  try {
    const address = await app.listen({
      port: config.port,
      host: '0.0.0.0'
    });

    app.log.info(
      `Iniciando Tecal Almoxarifado BFF em ${address} | Coligada=${config.rmCodColigada} | AssinaturaApp=${Boolean(config.enforceAppSignature)} | AdminAuth=${Boolean(config.requireAdminAuth)}`
    );
  } catch (err) {
    app.log.error(err);
    process.exit(1);
  }
}

start();
