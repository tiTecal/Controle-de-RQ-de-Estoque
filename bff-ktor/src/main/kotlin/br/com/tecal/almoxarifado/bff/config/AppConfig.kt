package br.com.tecal.almoxarifado.bff.config

import java.io.File
import java.time.ZoneId

data class AppConfig(
    val port: Int,
    val timezone: ZoneId,
    val jwtSecret: String,
    val jwtIssuer: String,
    val jwtAudience: String,
    val jwtExpirationHours: Long,
    val rmMockMode: Boolean,
    val rmBaseUrl: String,
    val rmCodColigada: Int,
    val rmCodSistemaSql: String,
    val rmContextExtra: String,
    val rmServiceUser: String,
    val rmServicePassword: String,
    val rmSentencaPendentes: String,
    val rmSentencaItens: String,
    val colRetiradoPor: String,
    val colDataRetirada: String,
    val colHoraRetirada: String,
    val dataRetiradaPattern: String
) {
    val rmContextoDataServer: String
        get() = "CODCOLIGADA=$rmCodColigada;$rmContextExtra;CODUSUARIO=$rmServiceUser"

    companion object {
        fun load(): AppConfig {
            val dotEnv = loadDotEnvFile()
            fun env(key: String, default: String): String =
                System.getenv(key)?.takeIf { it.isNotBlank() }
                    ?: dotEnv[key]?.takeIf { it.isNotBlank() }
                    ?: default

            return AppConfig(
                port = env("BFF_PORT", "8080").toIntOrNull() ?: 8080,
                timezone = ZoneId.of(env("BFF_TIMEZONE", "America/Sao_Paulo")),
                jwtSecret = env("BFF_JWT_SECRET", "tecal-almoxarifado-jwt-secret-change-in-prod-2026"),
                jwtIssuer = env("BFF_JWT_ISSUER", "tecal-almoxarifado-bff"),
                jwtAudience = env("BFF_JWT_AUDIENCE", "tecal-tablet-kiosk"),
                jwtExpirationHours = env("BFF_JWT_EXPIRATION_HOURS", "720").toLongOrNull() ?: 720L,
                rmMockMode = env("RM_MOCK_MODE", "true").equals("true", ignoreCase = true),
                rmBaseUrl = env("RM_BASE_URL", "http://127.0.0.1:8051").trimEnd('/'),
                rmCodColigada = env("RM_COD_COLIGADA", "1").toIntOrNull() ?: 1,
                rmCodSistemaSql = env("RM_COD_SISTEMA_SQL", "T"),
                rmContextExtra = env("RM_CONTEXT_EXTRA", "CODFILIAL=1;CODSISTEMA=T").trim(';'),
                rmServiceUser = env("RM_SERVICE_USER", "mestre"),
                rmServicePassword = env("RM_SERVICE_PASSWORD", ""),
                rmSentencaPendentes = env("RM_SENTENCA_PENDENTES", "TECAL.ALM.1110_PENDENTES"),
                rmSentencaItens = env("RM_SENTENCA_ITENS", "TECAL.ALM.1110_ITENS"),
                colRetiradoPor = env("RM_COL_RETIRADO_POR", "RETIRADO_POR").uppercase(),
                colDataRetirada = env("RM_COL_DATA_RETIRADA", "DATA_RETIRADA").uppercase(),
                colHoraRetirada = env("RM_COL_HORA_RETIRADA", "HORA_RETIRADA").uppercase(),
                dataRetiradaPattern = env("RM_DATA_RETIRADA_PATTERN", "yyyy-MM-dd")
            )
        }

        private fun loadDotEnvFile(): Map<String, String> {
            val file = File(".env")
            if (!file.exists() || !file.isFile) return emptyMap()
            return file.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
                .associate { line ->
                    val idx = line.indexOf('=')
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
                    key to value
                }
        }
    }
}

