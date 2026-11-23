import { buildApp } from './app.js';
import { loadAppConfig } from './config/env.js';

async function start() {
  const config = loadAppConfig();

  const app = await buildApp({ config });

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
