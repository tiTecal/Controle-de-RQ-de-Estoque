package br.com.tecal.almoxarifado.data.repository

import br.com.tecal.almoxarifado.data.local.CachedMovementItemEntity
import br.com.tecal.almoxarifado.data.local.CachedPendingMovementEntity
import br.com.tecal.almoxarifado.data.local.SessionPreferences
import br.com.tecal.almoxarifado.data.local.WithdrawalDao
import br.com.tecal.almoxarifado.data.model.AlmoxarifadoUserDto
import br.com.tecal.almoxarifado.data.model.ApiErrorResponse
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
import br.com.tecal.almoxarifado.data.remote.DynamicRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ApiOutcome<out T> {
    data class Success<T>(val data: T, val fromOfflineCache: Boolean = false) : ApiOutcome<T>()
    data class Error(
        val message: String,
        val isSessionExpired: Boolean = false,
        val isInvalidWithdrawerCredentials: Boolean = false,
        val isAlreadyWithdrawn: Boolean = false,
        val isNetworkFailure: Boolean = false
    ) : ApiOutcome<Nothing>()
}

class WithdrawalRepository(
    private val retrofitClient: DynamicRetrofitClient,
    private val dao: WithdrawalDao,
    private val sessionPreferences: SessionPreferences
) {

    suspend fun clearLocalSecurityCache() {
        runCatching { dao.clearAllCache() }
    }

    suspend fun loginSessaoEstoque(login: String, senha: String): ApiOutcome<EstoqueLoginResponse> {
        return try {
            val response = retrofitClient.api().loginEstoque(
                EstoqueLoginRequest(login = login.trim(), senha = senha)
            )
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                sessionPreferences.saveEstoqueSession(
                    token = body.token,
                    usuario = body.usuarioEstoque,
                    modoSimulacao = body.modoSimulacaoRm
                )
                ApiOutcome.Success(body)
            } else {
                val apiError = parseError(response.errorBody()?.string())
                ApiOutcome.Error(
                    message = apiError?.mensagem ?: "Falha ao autenticar usuário do estoque (${response.code()})."
                )
            }
        } catch (_: Exception) {
            ApiOutcome.Error(
                message = "Sem conexão com o servidor corporativo (${sessionPreferences.bffBaseUrl}). Verifique a rede da Tecal.",
                isNetworkFailure = true
            )
        }
    }

    suspend fun verifyAdminPinRemote(pin: String): ApiOutcome<VerifyAdminPinResponse> {
        return try {
            val response = retrofitClient.api().verifyAdminPin(
                VerifyAdminPinRequest(pin = pin.trim())
            )
            if (response.isSuccessful && response.body() != null) {
                ApiOutcome.Success(response.body()!!)
            } else {
                val apiError = parseError(response.errorBody()?.string())
                ApiOutcome.Error(
                    message = apiError?.mensagem ?: "PIN administrativo inválido (${response.code()})."
                )
            }
        } catch (_: Exception) {
            ApiOutcome.Error(
                message = "Servidor indisponível para validar PIN.",
                isNetworkFailure = true
            )
        }
    }

    suspend fun fetchPending1110Movements(): ApiOutcome<List<PendingMovementDto>> =
        withContext(Dispatchers.IO) {
            try {
                val response = retrofitClient.api().listarMovimentos1110Pendentes()
                when {
                    response.isSuccessful && response.body() != null -> {
                        val list = response.body()!!.filter { it.codColigada == 2 && it.codTmv.trim() == "1.1.10" }
                        runCatching {
                            dao.replaceAllPendingMovements(list.map(CachedPendingMovementEntity::fromDto))
                        }
                        ApiOutcome.Success(list, fromOfflineCache = false)
                    }
                    response.code() == 401 -> {
                        sessionPreferences.clearEstoqueSession()
                        clearLocalSecurityCache()
                        ApiOutcome.Error(
                            message = "Sessão do estoque expirada. Faça login novamente.",
                            isSessionExpired = true
                        )
                    }
                    else -> {
                        val apiError = parseError(response.errorBody()?.string())
                        ApiOutcome.Error(
                            message = apiError?.mensagem ?: "Erro ao buscar movimentos 1.1.10 (${response.code()})."
                        )
                    }
                }
            } catch (_: Exception) {
                val legacyMockIds = setOf(45812, 45815, 45819)
                val cached = dao.getAllPendingMovements()
                    .mapNotNull { it.toDtoOrNull() }
                    .filter { it.codColigada == 2 && it.idMov !in legacyMockIds }
                if (cached.isNotEmpty()) {
                    ApiOutcome.Success(cached, fromOfflineCache = true)
                } else {
                    ApiOutcome.Error(
                        message = "Servidor indisponível no momento (${sessionPreferences.bffBaseUrl}). Tentando reconectar...",
                        isNetworkFailure = true
                    )
                }
            }
        }

    suspend fun fetchMovementItems(idMov: Int): ApiOutcome<List<MovementItemDto>> =
        withContext(Dispatchers.IO) {
            try {
                val response = retrofitClient.api().listarItensMovimento1110(idMov)
                when {
                    response.isSuccessful && response.body() != null -> {
                        val items = response.body()!!
                        runCatching {
                            dao.replaceMovementItems(idMov, items.map(CachedMovementItemEntity::fromDto))
                        }
                        ApiOutcome.Success(items, fromOfflineCache = false)
                    }
                    response.code() == 401 -> {
                        sessionPreferences.clearEstoqueSession()
                        clearLocalSecurityCache()
                        ApiOutcome.Error(
                            message = "Sessão do estoque expirada. Faça login novamente.",
                            isSessionExpired = true
                        )
                    }
                    else -> {
                        val apiError = parseError(response.errorBody()?.string())
                        ApiOutcome.Error(
                            message = apiError?.mensagem ?: "Erro ao carregar itens do movimento ($idMov)."
                        )
                    }
                }
            } catch (_: Exception) {
                val cached = dao.getItemsForMovement(idMov).mapNotNull { it.toDtoOrNull() }
                if (cached.isNotEmpty()) {
                    ApiOutcome.Success(cached, fromOfflineCache = true)
                } else {
                    ApiOutcome.Error(
                        message = "Não foi possível carregar os itens do movimento (falha de rede).",
                        isNetworkFailure = true
                    )
                }
            }
        }

    suspend fun confirmarBaixa100PorCento(
        idMov: Int,
        loginRetirante: String,
        senhaRetirante: String,
        idempotencyKey: String
    ): ApiOutcome<ConfirmWithdrawalResponse> {
        return try {
            val response = retrofitClient.api().confirmarBaixa1110(
                idMov = idMov,
                request = ConfirmWithdrawalRequest(
                    login = loginRetirante.trim(),
                    senha = senhaRetirante,
                    confirmado100Porcento = true,
                    idempotencyKey = idempotencyKey
                )
            )
            when {
                response.isSuccessful && response.body() != null -> {
                    dao.deleteMovementById(idMov)
                    dao.deleteItemsForMovement(idMov)
                    ApiOutcome.Success(response.body()!!)
                }
                response.code() == 401 -> {
                    sessionPreferences.clearEstoqueSession()
                    clearLocalSecurityCache()
                    ApiOutcome.Error(
                        message = "Sessão do estoque no tablet expirou. Refaça o login do estoque.",
                        isSessionExpired = true
                    )
                }
                response.code() == 403 -> {
                    val apiError = parseError(response.errorBody()?.string())
                    ApiOutcome.Error(
                        message = apiError?.mensagem ?: "Login ou senha do retirante inválidos.",
                        isInvalidWithdrawerCredentials = true
                    )
                }
                response.code() == 409 -> {
                    dao.deleteMovementById(idMov)
                    dao.deleteItemsForMovement(idMov)
                    val apiError = parseError(response.errorBody()?.string())
                    ApiOutcome.Error(
                        message = apiError?.mensagem ?: "Este movimento 1.1.10 já foi baixado anteriormente.",
                        isAlreadyWithdrawn = true
                    )
                }
                else -> {
                    val apiError = parseError(response.errorBody()?.string())
                    ApiOutcome.Error(
                        message = apiError?.mensagem ?: "Servidor recusou a baixa (${response.code()})."
                    )
                }
            }
        } catch (_: Exception) {
            ApiOutcome.Error(
                message = "Falha de comunicação com o servidor ao registrar a baixa. Verifique o Wi-Fi/Rede e tente novamente.",
                isNetworkFailure = true
            )
        }
    }

    suspend fun fetchUsers(): ApiOutcome<List<AlmoxarifadoUserDto>> {
        return try {
            val response = retrofitClient.api().listarUsuarios()
            if (response.isSuccessful && response.body() != null) {
                ApiOutcome.Success(response.body()!!)
            } else if (response.code() == 401) {
                sessionPreferences.clearEstoqueSession()
                clearLocalSecurityCache()
                ApiOutcome.Error(
                    message = "Sessão expirada. Faça login novamente.",
                    isSessionExpired = true
                )
            } else {
                val apiError = parseError(response.errorBody()?.string())
                ApiOutcome.Error(
                    message = apiError?.mensagem ?: "Erro ao listar colaboradores (${response.code()})."
                )
            }
        } catch (_: Exception) {
            ApiOutcome.Error(
                message = "Falha de conexão com o servidor ao consultar colaboradores.",
                isNetworkFailure = true
            )
        }
    }

    suspend fun lookupEmployeeByExactCpf(cpf: String): ApiOutcome<List<AlmoxarifadoUserDto>> {
        return try {
            val response = retrofitClient.api().lookupUsuarioPorCpfExato(LookupCpfRequest(cpf = cpf))
            if (response.isSuccessful && response.body() != null) {
                ApiOutcome.Success(response.body()!!)
            } else if (response.code() == 401) {
                sessionPreferences.clearEstoqueSession()
                clearLocalSecurityCache()
                ApiOutcome.Error(
                    message = "Sessão expirada. Faça login novamente.",
                    isSessionExpired = true
                )
            } else {
                val apiError = parseError(response.errorBody()?.string())
                ApiOutcome.Error(
                    message = apiError?.mensagem ?: "Erro ao consultar CPF (${response.code()})."
                )
            }
        } catch (_: Exception) {
            ApiOutcome.Error(
                message = "Sem conexão para validar CPF completo no servidor.",
                isNetworkFailure = true
            )
        }
    }

    suspend fun upsertUser(
        adminToken: String,
        request: UpsertUserRequest
    ): ApiOutcome<AlmoxarifadoUserDto> {
        return try {
            val response = retrofitClient.api().salvarUsuario(
                adminToken = adminToken,
                request = request
            )
            if (response.isSuccessful && response.body() != null) {
                ApiOutcome.Success(response.body()!!)
            } else {
                val apiError = parseError(response.errorBody()?.string())
                ApiOutcome.Error(
                    message = apiError?.mensagem ?: "Erro ao salvar usuário (${response.code()})."
                )
            }
        } catch (_: Exception) {
            ApiOutcome.Error(
                message = "Falha de conexão com o servidor ao salvar usuário.",
                isNetworkFailure = true
            )
        }
    }

    suspend fun updateUserStatus(
        adminToken: String,
        login: String,
        ativo: Boolean
    ): ApiOutcome<AlmoxarifadoUserDto> {
        return try {
            val response = retrofitClient.api().alterarStatusUsuario(
                adminToken = adminToken,
                login = login,
                request = UpdateUserStatusRequest(ativo = ativo)
            )
            if (response.isSuccessful && response.body() != null) {
                ApiOutcome.Success(response.body()!!)
            } else {
                val apiError = parseError(response.errorBody()?.string())
                ApiOutcome.Error(
                    message = apiError?.mensagem ?: "Erro ao alterar status do usuário (${response.code()})."
                )
            }
        } catch (_: Exception) {
            ApiOutcome.Error(
                message = "Falha de conexão com o servidor ao alterar status do usuário.",
                isNetworkFailure = true
            )
        }
    }

    private fun parseError(rawBody: String?): ApiErrorResponse? {
        if (rawBody.isNullOrBlank()) return null
        return try {
            retrofitClient.json.decodeFromString<ApiErrorResponse>(rawBody)
        } catch (_: Exception) {
            null
        }
    }
}
