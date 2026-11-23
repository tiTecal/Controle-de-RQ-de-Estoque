import crypto from 'node:crypto';

export const INSECURE_DEFAULT_JWT_SECRET = 'tecal-almoxarifado-jwt-secret-change-in-prod-2026';

/**
 * Chave HMAC de atestação do cliente (Android <-> BFF) para bloquear acesso direto via curl/Postman/scripts
 * de terceiros na rede local (Anti-Engenharia Reversa / Anti-Scraping).
 */
export const DEFAULT_APP_SIGNING_SECRET =
  'Tecal@2026#Almox!Sec7b9e4a1f8c3d2094f6e1a7c8b5d4e3f2';

export function resolveSafeJwtSecret(configuredSecret: string | undefined, isTest: boolean): {
  secret: string;
  usedEphemeralFallback: boolean;
} {
  const clean = (configuredSecret || '').trim();
  if (clean && clean !== INSECURE_DEFAULT_JWT_SECRET && clean.length >= 32) {
    return { secret: clean, usedEphemeralFallback: false };
  }

  if (isTest && clean) {
    return { secret: clean, usedEphemeralFallback: false };
  }

  // Nunca usar chave padrão conhecida publicamente em produção; gera segredo criptográfico efêmero
  return {
    secret: crypto.randomBytes(64).toString('hex'),
    usedEphemeralFallback: true
  };
}

export function onlyDigits(val: string): string {
  return (val || '').replace(/\D+/g, '');
}

/**
 * LGPD: Mascara CPF mantendo exclusivamente os 3 primeiros dígitos ("158.***.***-**").
 * O CPF completo jamais sai do servidor BFF.
 */
export function maskCpfLgpd(rawCpf: string): string {
  const digits = onlyDigits(rawCpf);
  if (digits.length >= 3) {
    return `${digits.slice(0, 3)}.***.***-**`;
  }
  return '***.***.***-**';
}

export function isCpfNumericLogin(val: string): boolean {
  const trimmed = (val || '').trim();
  const digits = onlyDigits(trimmed);
  return digits.length >= 9 && digits.length === trimmed.length;
}

/**
 * Converte nome completo para identificador curto seguro ("primeiro.ultimo") sem acentos ou PII sensível.
 */
export function buildSafeNameSlug(fullName: string): string {
  const parts = (fullName || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z\s]/g, ' ')
    .split(/\s+/)
    .filter(Boolean);

  if (parts.length >= 2) {
    return `${parts[0]}.${parts[parts.length - 1]}`.slice(0, 50);
  }
  if (parts.length === 1) {
    return parts[0].slice(0, 50);
  }
  return 'colaborador';
}

/**
 * Gera referência opaca não reversível para identificar colaboradores do RM sem expor
 * CODUSUARIO ou CPF em tráfego JSON, telas ou armazenamento local do dispositivo.
 */
export function createOpaqueRmRef(secret: string, uniqueSeed: string): string {
  const hmac = crypto.createHmac('sha256', secret).update(uniqueSeed).digest('hex');
  return `rm_${hmac.slice(0, 16)}`;
}

/**
 * Comparação constant-time de strings hexadecimais / tokens para evitar timing attacks.
 */
export function safeEqualStrings(a: string, b: string): boolean {
  if (!a || !b) return false;
  const bufA = Buffer.from(a, 'utf8');
  const bufB = Buffer.from(b, 'utf8');
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

/**
 * Gera hash SHA-256 de PIN administrativo com salt fixo de domínio para comparação segura.
 */
export function hashAdminPin(pin: string): string {
  return crypto.createHash('sha256').update(`TECAL_ALM_PIN_V1:${pin.trim()}`).digest('hex').toLowerCase();
}

export function verifyClientHmacSignature(params: {
  secret: string;
  method: string;
  path: string;
  timestampHeader: string | undefined;
  nonceHeader: string | undefined;
  signatureHeader: string | undefined;
  usedNonces: Map<string, number>;
  nowMs?: number;
  maxSkewMs?: number;
}): { ok: boolean; reason?: string } {
  const now = params.nowMs ?? Date.now();
  const maxSkewMs = params.maxSkewMs ?? 300_000;

  const tsRaw = (params.timestampHeader || '').trim();
  const nonce = (params.nonceHeader || '').trim();
  const signature = (params.signatureHeader || '').trim().toLowerCase();

  if (!tsRaw || !nonce || !signature) {
    return { ok: false, reason: 'Cabeçalhos de integridade do aplicativo ausentes.' };
  }

  const ts = Number(tsRaw);
  if (!Number.isFinite(ts) || Math.abs(now - ts) > maxSkewMs) {
    return { ok: false, reason: 'Assinatura expirada ou relógio do dispositivo dessincronizado.' };
  }

  if (nonce.length < 12 || nonce.length > 80) {
    return { ok: false, reason: 'Nonce de requisição inválido.' };
  }

  // Limpa nonces expirados e previne Replay Attack
  if (params.usedNonces.size > 5_000) {
    for (const [k, exp] of params.usedNonces.entries()) {
      if (exp <= now) params.usedNonces.delete(k);
    }
    if (params.usedNonces.size > 5_000) {
      params.usedNonces.clear();
    }
  }

  const existingExp = params.usedNonces.get(nonce);
  if (existingExp && existingExp > now) {
    return { ok: false, reason: 'Requisição duplicada (replay detectado).' };
  }

  // Caminho sem query string para assinatura canônica estável
  const cleanPath = params.path.split('?')[0];
  const canonical = `${params.method.toUpperCase()}\n${cleanPath}\n${tsRaw}\n${nonce}`;
  const expected = crypto
    .createHmac('sha256', params.secret)
    .update(canonical)
    .digest('hex')
    .toLowerCase();

  if (!safeEqualStrings(expected, signature)) {
    return { ok: false, reason: 'Assinatura de integridade do cliente inválida.' };
  }

  params.usedNonces.set(nonce, now + maxSkewMs);
  return { ok: true };
}

export class InMemoryRateLimiter {
  private readonly buckets = new Map<string, { count: number; resetAt: number }>();

  checkAndIncrement(key: string, maxAttempts: number, windowMs: number): {
    allowed: boolean;
    retryAfterSeconds: number;
  } {
    const now = Date.now();
    if (this.buckets.size > 5_000) {
      for (const [k, bucket] of this.buckets.entries()) {
        if (bucket.resetAt <= now) this.buckets.delete(k);
      }
    }

    const existing = this.buckets.get(key);
    if (!existing || existing.resetAt <= now) {
      this.buckets.set(key, { count: 1, resetAt: now + windowMs });
      return { allowed: true, retryAfterSeconds: 0 };
    }

    if (existing.count >= maxAttempts) {
      const retryAfterSeconds = Math.max(1, Math.ceil((existing.resetAt - now) / 1000));
      return { allowed: false, retryAfterSeconds };
    }

    existing.count += 1;
    return { allowed: true, retryAfterSeconds: 0 };
  }

  isBlocked(key: string, maxAttempts: number): { blocked: boolean; retryAfterSeconds: number } {
    const now = Date.now();
    const existing = this.buckets.get(key);
    if (!existing || existing.resetAt <= now) {
      if (existing) this.buckets.delete(key);
      return { blocked: false, retryAfterSeconds: 0 };
    }
    if (existing.count >= maxAttempts) {
      return {
        blocked: true,
        retryAfterSeconds: Math.max(1, Math.ceil((existing.resetAt - now) / 1000))
      };
    }
    return { blocked: false, retryAfterSeconds: 0 };
  }

  recordFailure(key: string, windowMs: number): void {
    const now = Date.now();
    const existing = this.buckets.get(key);
    if (!existing || existing.resetAt <= now) {
      this.buckets.set(key, { count: 1, resetAt: now + windowMs });
    } else {
      existing.count += 1;
    }
  }

  reset(key: string): void {
    this.buckets.delete(key);
  }
}

