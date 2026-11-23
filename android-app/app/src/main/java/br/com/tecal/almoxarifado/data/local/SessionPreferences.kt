package br.com.tecal.almoxarifado.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import br.com.tecal.almoxarifado.security.SecurityGuard
import java.util.concurrent.ConcurrentHashMap

class SessionPreferences(context: Context) {

    private val prefs: SharedPreferences = createSecurePrefs(context)

    var bffBaseUrl: String
        get() {
            val saved = prefs.getString(KEY_BFF_URL, DEFAULT_BFF_URL) ?: DEFAULT_BFF_URL
            return if (saved.contains("10.0.2.2") || !SecurityGuard.isSafeCorporateUrl(saved)) {
                DEFAULT_BFF_URL
            } else {
                saved
            }
        }
        set(value) {
            val normalized = value.trim().let { if (it.endsWith("/")) it else "$it/" }
            require(SecurityGuard.isSafeCorporateUrl(normalized)) {
                "URL bloqueada pela política de segurança corporativa."
            }
            prefs.edit().putString(KEY_BFF_URL, normalized).apply()
        }

    var jwtToken: String?
        get() = prefs.getString(KEY_JWT_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_JWT_TOKEN, value).apply()
        }

    var usuarioEstoque: String?
        get() = prefs.getString(KEY_USUARIO_ESTOQUE, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().putString(KEY_USUARIO_ESTOQUE, value).apply()
        }

    var modoSimulacaoRm: Boolean
        get() = false
        set(value) {
            prefs.edit().putBoolean(KEY_MODO_SIMULACAO, false).apply()
        }

    fun verifyOfflineAdminPin(pin: String): Boolean {
        val storedDigest = prefs.getString(KEY_ADMIN_PIN_DIGEST, null)
            ?: SecurityGuard.DEFAULT_ADMIN_PIN_DIGEST
        return SecurityGuard.hashAdminPin(pin) == storedDigest
    }

    fun updateOfflineAdminPin(newPin: String) {
        val trimmed = newPin.trim()
        if (trimmed.isNotBlank()) {
            prefs.edit()
                .putString(KEY_ADMIN_PIN_DIGEST, SecurityGuard.hashAdminPin(trimmed))
                .remove(KEY_ADMIN_PIN_LEGACY)
                .apply()
        }
    }

    val isEstoqueLoggedIn: Boolean
        get() = !jwtToken.isNullOrBlank() && !usuarioEstoque.isNullOrBlank()

    fun saveEstoqueSession(token: String, usuario: String, modoSimulacao: Boolean) {
        prefs.edit()
            .putString(KEY_JWT_TOKEN, token)
            .putString(KEY_USUARIO_ESTOQUE, usuario)
            .putBoolean(KEY_MODO_SIMULACAO, modoSimulacao)
            .apply()
    }

    fun clearEstoqueSession() {
        prefs.edit()
            .remove(KEY_JWT_TOKEN)
            .remove(KEY_USUARIO_ESTOQUE)
            .remove(KEY_MODO_SIMULACAO)
            .apply()
    }

    private fun createSecurePrefs(context: Context): SharedPreferences {
        // Garante remoção de eventual arquivo legado não criptografado
        runCatching { context.deleteSharedPreferences("${PREFS_NAME}_fallback") }

        fun openEncrypted(): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        val securePrefs = runCatching { openEncrypted() }.getOrElse {
            // Se a MasterKey/keystore estiver corrompida, recria o arquivo criptografado limpo antes de desistir
            runCatching { context.deleteSharedPreferences(PREFS_NAME) }
            runCatching { openEncrypted() }.getOrElse {
                // Em último caso, usa cofre volátil somente em RAM — nunca grava segredos em XML texto claro
                InMemorySharedPreferences()
            }
        }

        if (securePrefs.contains(KEY_ADMIN_PIN_LEGACY)) {
            val legacy = securePrefs.getString(KEY_ADMIN_PIN_LEGACY, null)
            securePrefs.edit().apply {
                if (!legacy.isNullOrBlank() && !securePrefs.contains(KEY_ADMIN_PIN_DIGEST)) {
                    putString(KEY_ADMIN_PIN_DIGEST, SecurityGuard.hashAdminPin(legacy))
                }
                remove(KEY_ADMIN_PIN_LEGACY)
            }.apply()
        }

        return securePrefs
    }

    companion object {
        private const val PREFS_NAME = "tecal_almoxarifado_secure_prefs"
        private const val KEY_BFF_URL = "bff_base_url"
        private const val KEY_JWT_TOKEN = "estoque_jwt_token"
        private const val KEY_USUARIO_ESTOQUE = "usuario_estoque"
        private const val KEY_MODO_SIMULACAO = "modo_simulacao_rm"
        private const val KEY_ADMIN_PIN_LEGACY = "admin_pin"
        private const val KEY_ADMIN_PIN_DIGEST = "admin_pin_digest"

        const val DEFAULT_BFF_URL = "http://192.168.9.230:8080/"
    }
}

private class InMemorySharedPreferences : SharedPreferences {
    private val map = ConcurrentHashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(map)
    override fun getString(key: String?, defValue: String?): String? =
        if (key != null) (map[key] as? String) ?: defValue else defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        if (key != null) (map[key] as? MutableSet<String>) ?: defValues else defValues
    override fun getInt(key: String?, defValue: Int): Int =
        if (key != null) (map[key] as? Int) ?: defValue else defValue
    override fun getLong(key: String?, defValue: Long): Long =
        if (key != null) (map[key] as? Long) ?: defValue else defValue
    override fun getFloat(key: String?, defValue: Float): Float =
        if (key != null) (map[key] as? Float) ?: defValue else defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        if (key != null) (map[key] as? Boolean) ?: defValue else defValue
    override fun contains(key: String?): Boolean = key != null && map.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private val removals = HashSet<String>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
        override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) pending[key] = values }
        override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
        override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
        override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
        override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
        override fun remove(key: String?) = apply { if (key != null) removals.add(key) }
        override fun clear() = apply { clearRequested = true }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearRequested) map.clear()
            removals.forEach { map.remove(it) }
            pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
        }
    }
}

