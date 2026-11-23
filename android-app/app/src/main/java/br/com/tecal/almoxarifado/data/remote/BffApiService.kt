package br.com.tecal.almoxarifado.data.remote

import br.com.tecal.almoxarifado.data.local.SessionPreferences
import br.com.tecal.almoxarifado.data.model.AlmoxarifadoUserDto
import br.com.tecal.almoxarifado.data.model.ConfirmWithdrawalRequest
import br.com.tecal.almoxarifado.data.model.ConfirmWithdrawalResponse
import br.com.tecal.almoxarifado.data.model.EstoqueLoginRequest
import br.com.tecal.almoxarifado.data.model.EstoqueLoginResponse
import br.com.tecal.almoxarifado.data.model.LookupCpfRequest
import br.com.tecal.almoxarifado.data.model.MovementItemDto
import br.com.tecal.almoxarifado.data.model.PendingMovementDto
import br.com.tecal.almoxarifado.data.model.UpdateUserStatusRequest
import br.com.tecal.almoxarifado.data.model.UpsertUserRequest
import br.com.tecal.almoxarifado.data.model.VerifyAdminPinRequest
import br.com.tecal.almoxarifado.data.model.VerifyAdminPinResponse
import br.com.tecal.almoxarifado.security.SecurityGuard
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

interface BffApiService {

    @POST("api/v1/auth/login")
    suspend fun loginEstoque(
        @Body request: EstoqueLoginRequest
    ): Response<EstoqueLoginResponse>

    @POST("api/v1/admin/verify-pin")
    suspend fun verifyAdminPin(
        @Body request: VerifyAdminPinRequest
    ): Response<VerifyAdminPinResponse>

    @GET("api/v1/movimentos/pendentes")
    suspend fun listarMovimentos1110Pendentes(): Response<List<PendingMovementDto>>

    @GET("api/v1/movimentos/{idMov}/itens")
    suspend fun listarItensMovimento1110(
        @Path("idMov") idMov: Int
    ): Response<List<MovementItemDto>>

    @POST("api/v1/movimentos/{idMov}/baixar")
    suspend fun confirmarBaixa1110(
        @Path("idMov") idMov: Int,
        @Body request: ConfirmWithdrawalRequest
    ): Response<ConfirmWithdrawalResponse>

    @GET("api/v1/usuarios")
    suspend fun listarUsuarios(): Response<List<AlmoxarifadoUserDto>>

    @POST("api/v1/usuarios/lookup-cpf")
    suspend fun lookupUsuarioPorCpfExato(
        @Body request: LookupCpfRequest
    ): Response<List<AlmoxarifadoUserDto>>

    @POST("api/v1/usuarios")
    suspend fun salvarUsuario(
        @Header("X-Tecal-Admin-Token") adminToken: String,
        @Body request: UpsertUserRequest
    ): Response<AlmoxarifadoUserDto>

    @PATCH("api/v1/usuarios/{login}/status")
    suspend fun alterarStatusUsuario(
        @Header("X-Tecal-Admin-Token") adminToken: String,
        @Path("login") login: String,
        @Body request: UpdateUserStatusRequest
    ): Response<AlmoxarifadoUserDto>
}

class DynamicRetrofitClient(
    private val sessionPreferences: SessionPreferences
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            if (!SecurityGuard.isSafeCorporateUrl(original.url.toString())) {
                throw IOException("Destino bloqueado pela política de segurança corporativa.")
            }

            val firstResponse = chain.proceed(buildSignedRequest(original))
            val serverTimeMs = firstResponse.header("X-Tecal-Server-Time")?.toLongOrNull()
                ?: firstResponse.headers.getDate("Date")?.time
            if (serverTimeMs != null) {
                SecurityGuard.syncServerClock(serverTimeMs)
            }

            // Se o relógio do aparelho divergir além da janela HMAC, sincroniza pelo servidor e reenvia uma vez
            if (firstResponse.code == 403 && serverTimeMs != null) {
                val preview = runCatching { firstResponse.peekBody(512).string() }.getOrDefault("")
                if (preview.contains("UNAUTHORIZED_CLIENT")) {
                    firstResponse.close()
                    return@addInterceptor chain.proceed(buildSignedRequest(original))
                }
            }

            firstResponse
        }
        .build()

    private fun buildSignedRequest(original: Request): Request {
        val signed = SecurityGuard.signHttpRequest(original.method, original.url.encodedPath)
        val builder = original.newBuilder()
            .header("X-Tecal-Timestamp", signed.timestamp)
            .header("X-Tecal-Nonce", signed.nonce)
            .header("X-Tecal-Signature", signed.signature)

        sessionPreferences.jwtToken?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }
        return builder.build()
    }

    @Volatile
    private var cachedUrl: String = ""

    @Volatile
    private var cachedApi: BffApiService? = null

    fun api(): BffApiService {
        val normalized = normalizeBaseUrl(sessionPreferences.bffBaseUrl)
        val currentUrl = if (SecurityGuard.isSafeCorporateUrl(normalized)) {
            normalized
        } else {
            SessionPreferences.DEFAULT_BFF_URL
        }

        val existing = cachedApi
        if (existing != null && cachedUrl == currentUrl) {
            return existing
        }
        return synchronized(this) {
            val recheck = cachedApi
            if (recheck != null && cachedUrl == currentUrl) {
                recheck
            } else {
                val created = Retrofit.Builder()
                    .baseUrl(currentUrl)
                    .client(okHttpClient)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
                    .create(BffApiService::class.java)
                cachedUrl = currentUrl
                cachedApi = created
                created
            }
        }
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().ifEmpty { SessionPreferences.DEFAULT_BFF_URL }
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }
}
