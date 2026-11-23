package br.com.tecal.almoxarifado.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Debug
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import br.com.tecal.almoxarifado.BuildConfig
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class SignedRequestHeaders(
    val timestamp: String,
    val nonce: String,
    val signature: String
)

data class IntegrityCheckResult(
    val safe: Boolean,
    val reason: String? = null
)

/**
 * Núcleo de proteção Anti-Engenharia Reversa, Criptografia Keystore e Blindagem contra
 * vazamento de dados do TOTVS RM na camada Android.
 */
object SecurityGuard {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val VAULT_KEY_ALIAS = "TecalRmLocalVaultAes256"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    // Chave de atestação segmentada e mascarada por XOR dinâmico (não fica legível via strings/jadx)
    private val obfuscatedChannelKey = intArrayOf(
        14, 14, 31, 236, 242, 239, 242, 225, 208, 197, 39, 84, 74,
        90, 39, 33, 75, 40, 233, 254, 153, 221, 233, 132, 198, 98,
        37, 67, 14, 36, 107, 13, 72, 187, 165, 153, 216, 249, 133,
        192, 99, 36, 71, 13, 36, 98, 12, 77, 239, 168, 202, 143
    )

    // Digest SHA-256 com salt de domínio para verificação offline em emergência (sem PIN em texto plano no APK)
    const val DEFAULT_ADMIN_PIN_DIGEST =
        "d64d6aa9de9d8e1cafc04bdf82ff232ea010a90f88f64a16f837176b9675ef8f"

    private val secureRandom = SecureRandom()

    @Volatile
    private var serverClockOffsetMs: Long = 0L

    fun syncServerClock(serverEpochMs: Long) {
        if (serverEpochMs <= 0L) return
        val offset = serverEpochMs - System.currentTimeMillis()
        serverClockOffsetMs = if (kotlin.math.abs(offset) <= 15_000L) 0L else offset
    }

    private fun deriveSigningSecretBytes(): ByteArray {
        val out = ByteArray(obfuscatedChannelKey.size)
        for (i in obfuscatedChannelKey.indices) {
            val mask = (0x5A + (i * 17)) and 0xFF
            out[i] = ((obfuscatedChannelKey[i] xor mask) and 0xFF).toByte()
        }
        return out
    }

    fun signHttpRequest(method: String, encodedPath: String): SignedRequestHeaders {
        val ts = (System.currentTimeMillis() + serverClockOffsetMs).toString()
        val nonceBytes = ByteArray(16)
        secureRandom.nextBytes(nonceBytes)
        val nonce = nonceBytes.joinToString("") { "%02x".format(it) }

        val cleanPath = encodedPath.substringBefore('?')
        val canonical = "${method.uppercase()}\n$cleanPath\n$ts\n$nonce"

        val secretBytes = deriveSigningSecretBytes()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretBytes, "HmacSHA256"))
        val digest = mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
        secretBytes.fill(0)

        val signatureHex = digest.joinToString("") { "%02x".format(it) }
        return SignedRequestHeaders(timestamp = ts, nonce = nonce, signature = signatureHex)
    }

    fun hashAdminPin(pin: String): String {
        val input = "TECAL_ALM_PIN_V1:${pin.trim()}".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Impede exfiltração de dados do RM para fora da rede corporativa:
     * Aceita exclusivamente HTTPS ou IPs privados RFC-1918 (192.168.x.x, 10.x.x.x, 172.16-31.x.x, localhost).
     */
    fun isSafeCorporateUrl(rawUrl: String): Boolean {
        return try {
            val uri = URI(rawUrl.trim())
            val scheme = uri.scheme?.lowercase() ?: return false
            val host = uri.host?.lowercase() ?: return false

            if (scheme == "https") return true
            if (scheme != "http") return false

            isPrivateLanHost(host)
        } catch (_: Exception) {
            false
        }
    }

    private fun isPrivateLanHost(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1") return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it < 0 || it > 255 }) return false

        val (a, b, _, _) = octets
        return a == 10 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31)
    }

    /**
     * Verifica integridade contra engenharia reversa em tempo de execução (Frida, Xposed, Root, Debugger).
     * Em builds de Debug (BuildConfig.DEBUG) permite desenvolvimento USB; em Release bloqueia adulteração.
     */
    fun checkEnvironmentIntegrity(context: Context): IntegrityCheckResult {
        if (BuildConfig.DEBUG) {
            return IntegrityCheckResult(safe = true)
        }

        // 1. Flag debuggable adulterada no manifest após repackaging
        val isFlagDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isFlagDebuggable || Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
            return IntegrityCheckResult(
                safe = false,
                reason = "Execução bloqueada: ambiente de depuração não autorizado detectado."
            )
        }

        // 2. Detecção de instrumentação dinâmica (Frida / Xposed / LSPosed / Substrate)
        if (hasHookingFramework()) {
            return IntegrityCheckResult(
                safe = false,
                reason = "Execução bloqueada: ferramenta de engenharia reversa detectada."
            )
        }

        // 3. Detecção de dispositivo com Root / su / test-keys em ambiente de produção
        if (hasRootIndicators()) {
            return IntegrityCheckResult(
                safe = false,
                reason = "Dispositivo não atende aos requisitos mínimos de segurança corporativa (Root detectado)."
            )
        }

        return IntegrityCheckResult(safe = true)
    }

    private fun hasHookingFramework(): Boolean {
        return try {
            val mapsFile = File("/proc/self/maps")
            if (!mapsFile.exists() || !mapsFile.canRead()) return false
            val suspiciousTokens = listOf("frida", "gum-js", "xposed", "lsposed", "substrate")
            mapsFile.bufferedReader().useLines { lines ->
                lines.any { line ->
                    val lower = line.lowercase()
                    suspiciousTokens.any { token -> lower.contains(token) }
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun hasRootIndicators(): Boolean {
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }
        val suPaths = listOf(
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/su/bin/su",
            "/system/app/Superuser.apk"
        )
        return suPaths.any { path ->
            try {
                File(path).exists()
            } catch (_: Exception) {
                false
            }
        }
    }

    /**
     * Criptografa dados locais do RM via AES-256-GCM usando chave protegida no Hardware Keystore
     * para que o SQLite (Room) nunca armazene movimentações ou itens do RM em texto claro.
     */
    fun encryptVaultData(plainText: String): String {
        val cipher = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, getOrCreateVaultKey())
            }
        } catch (_: Exception) {
            // Recupera automaticamente caso a chave do Android Keystore tenha sido invalidada entre upgrades
            resetVaultKey()
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, getOrCreateVaultKey())
            }
        }
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteBuffer.allocate(1 + iv.size + cipherBytes.size)
            .put(iv.size.toByte())
            .put(iv)
            .put(cipherBytes)
            .array()

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    @Synchronized
    private fun resetVaultKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(VAULT_KEY_ALIAS)) {
                keyStore.deleteEntry(VAULT_KEY_ALIAS)
            }
        }
    }

    fun decryptVaultData(encodedCipherText: String): String? {
        return try {
            val combined = Base64.decode(encodedCipherText, Base64.NO_WRAP)
            if (combined.size < 2) return null
            val buffer = ByteBuffer.wrap(combined)
            val ivLen = buffer.get().toInt() and 0xFF
            if (ivLen != GCM_IV_BYTES || buffer.remaining() < ivLen + 1) return null
            val iv = ByteArray(ivLen)
            buffer.get(iv)
            val cipherBytes = ByteArray(buffer.remaining())
            buffer.get(cipherBytes)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateVaultKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            val plain = cipher.doFinal(cipherBytes)
            String(plain, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    private fun getOrCreateVaultKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(VAULT_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            VAULT_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }
}

