import { buildApp } from '../dist/app.js';
import { loadAppConfig } from '../dist/config/env.js';

async function runBenchmark() {
  const config = loadAppConfig();
  config.port = 8081; // porta dedicada para o teste
  const app = await buildApp({ config });
  await app.listen({ port: 8081, host: '127.0.0.1' });

  console.log('--- INICIANDO BENCHMARK DE PERFORMANCE E LATENCIA ---');

  // 1. Aquecimento (Warm-up)
  for (let i = 0; i < 50; i++) {
    await fetch('http://127.0.0.1:8081/api/v1/health');
  }

  // 2. Medição Healthcheck (1.000 requisições)
  const healthTimes = [];
  const startHealth = performance.now();
  const N_HEALTH = 1000;
  for (let i = 0; i < N_HEALTH; i++) {
    const t0 = performance.now();
    await fetch('http://127.0.0.1:8081/api/v1/health');
    healthTimes.push(performance.now() - t0);
  }
  const totalHealth = performance.now() - startHealth;

  // 3. Login do Estoque (JWT + Autenticação)
  const loginTimes = [];
  let token = '';
  const N_LOGIN = 200;
  for (let i = 0; i < N_LOGIN; i++) {
    const t0 = performance.now();
    const res = await fetch('http://127.0.0.1:8081/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ login: 'almoxarife', senha: 'tecal123' })
    });
    loginTimes.push(performance.now() - t0);
    if (i === 0) {
      const data = await res.json();
      token = data.token;
    }
  }

  // 4. Listagem de Pendentes Autenticada (200 requisições)
  const listTimes = [];
  const N_LIST = 200;
  for (let i = 0; i < N_LIST; i++) {
    const t0 = performance.now();
    await fetch('http://127.0.0.1:8081/api/v1/movimentos/pendentes', {
      headers: { Authorization: `Bearer ${token}` }
    });
    listTimes.push(performance.now() - t0);
  }

  // 5. Consulta de Itens do Movimento (200 requisições)
  const itemTimes = [];
  const N_ITEMS = 200;
  for (let i = 0; i < N_ITEMS; i++) {
    const t0 = performance.now();
    await fetch('http://127.0.0.1:8081/api/v1/movimentos/45812/itens', {
      headers: { Authorization: `Bearer ${token}` }
    });
    itemTimes.push(performance.now() - t0);
  }

  // Cálculos estatísticos
  function calcStats(arr) {
    const sorted = [...arr].sort((a, b) => a - b);
    const avg = arr.reduce((a, b) => a + b, 0) / arr.length;
    const p50 = sorted[Math.floor(sorted.length * 0.5)];
    const p95 = sorted[Math.floor(sorted.length * 0.95)];
    const p99 = sorted[Math.floor(sorted.length * 0.99)];
    return {
      avg: avg.toFixed(2),
      p50: p50.toFixed(2),
      p95: p95.toFixed(2),
      p99: p99.toFixed(2),
      min: sorted[0].toFixed(2),
      max: sorted[sorted.length - 1].toFixed(2)
    };
  }

  console.log('\n--- RESULTADOS DO BENCHMARK ---');
  console.log(`Healthcheck (${N_HEALTH} reqs): Total = ${totalHealth.toFixed(1)}ms | Vazão = ${(N_HEALTH / (totalHealth / 1000)).toFixed(0)} reqs/seg`);
  console.log('Latência Health (ms):', JSON.stringify(calcStats(healthTimes)));
  console.log('Latência Login Estoque (ms):', JSON.stringify(calcStats(loginTimes)));
  console.log('Latência Listar Pendentes (ms):', JSON.stringify(calcStats(listTimes)));
  console.log('Latência Listar Itens (ms):', JSON.stringify(calcStats(itemTimes)));

  await app.close();
}

runBenchmark().catch(console.error);

