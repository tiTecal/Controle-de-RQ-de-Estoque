import crypto from 'node:crypto';

/**
 * Utilitário criptográfico de senhas compatível 100% entre o Node.js e a função
 * nativa HASHBYTES('SHA2_256', CAST(@SALT AS NVARCHAR(64)) + N':' + @SENHA_TEXTO) do SQL Server.
 *
 * No SQL Server, tipos NVARCHAR utilizam codificação UTF-16LE (2 bytes por caractere),
 * exatamente igual ao encoding 'utf16le' do Buffer no Node.js.
 */
export class PasswordHasher {
  static generateSalt(): string {
    return crypto.randomBytes(16).toString('hex').toLowerCase();
  }

  static hashPassword(password: string, salt: string): string {
    const cleanSalt = salt.trim();
    const payload = Buffer.from(`${cleanSalt}:${password}`, 'utf16le');
    return crypto.createHash('sha256').update(payload).digest('hex').toLowerCase();
  }

  static hashPasswordUtf8(password: string, salt: string): string {
    const cleanSalt = salt.trim();
    const payload = Buffer.from(`${cleanSalt}:${password}`, 'utf8');
    return crypto.createHash('sha256').update(payload).digest('hex').toLowerCase();
  }

  static verifyPassword(password: string, salt: string, storedHash: string): boolean {
    if (!password || !salt || !storedHash) return false;

    const cleanStored = storedHash.trim().toLowerCase();
    const expectedUtf16 = this.hashPassword(password, salt);
    if (this.safeCompareHex(expectedUtf16, cleanStored)) {
      return true;
    }

    // Fallback caso o hash tenha sido gerado via VARCHAR (UTF-8 / ASCII)
    const expectedUtf8 = this.hashPasswordUtf8(password, salt);
    return this.safeCompareHex(expectedUtf8, cleanStored);
  }

  private static safeCompareHex(a: string, b: string): boolean {
    if (a.length !== b.length) return false;
    try {
      const bufA = Buffer.from(a, 'utf8');
      const bufB = Buffer.from(b, 'utf8');
      return crypto.timingSafeEqual(bufA, bufB);
    } catch {
      return false;
    }
  }
}

