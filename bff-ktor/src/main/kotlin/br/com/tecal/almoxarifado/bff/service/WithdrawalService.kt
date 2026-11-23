package br.com.tecal.almoxarifado.bff.service

import br.com.tecal.almoxarifado.bff.config.AppConfig
import br.com.tecal.almoxarifado.bff.domain.BusinessValidationException
import br.com.tecal.almoxarifado.bff.domain.ConfirmWithdrawalRequest
import br.com.tecal.almoxarifado.bff.domain.ConfirmWithdrawalResponse
import br.com.tecal.almoxarifado.bff.domain.EstoqueLoginRequest
import br.com.tecal.almoxarifado.bff.domain.EstoqueLoginResponse
import br.com.tecal.almoxarifado.bff.domain.InvalidRmCredentialsException
import br.com.tecal.almoxarifado.bff.domain.MovementItemDto
import br.com.tecal.almoxarifado.bff.domain.PendingMovementDto
import br.com.tecal.almoxarifado.bff.rm.TotvsRmGateway
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

class WithdrawalService(
    private val config: AppConfig,
    private val rmGateway: TotvsRmGateway
) {
    private val movementLocks = ConcurrentHashMap<Int, Mutex>()
    private val idempotencyCache = ConcurrentHashMap<String, ConfirmWithdrawalResponse>()

    private val dateFormatter = DateTimeFormatter.ofPattern(config.dataRetiradaPattern)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    /**
     * Autentica a sessão persistente do tablet (usuário do estoque) no TOTVS RM
     * para deixar o sistema logado ao lado da porta listando os movimentos 1.1.10 pendentes.
     */
    suspend fun loginSessaoEstoque(request: EstoqueLoginRequest): EstoqueLoginResponse {
        val cleanLogin = request.login.trim()
        if (cleanLogin.isEmpty() || request.senha.isEmpty()) {
            throw BusinessValidationException("Informe o login e a senha do usuário do estoque.")
        }

        val authenticated = rmGateway.authenticateRmUser(cleanLogin, request.senha)
        if (!authenticated) {
            throw InvalidRmCredentialsException(
                message = "Credenciais do usuário do estoque inválidas no TOTVS RM.",
                isWithdrawalConfirmation = false
            )
        }

        val expiresAt = Instant.now().plus(config.jwtExpirationHours, ChronoUnit.HOURS)
        val token = JWT.create()
            .withIssuer(config.jwtIssuer)
            .withAudience(config.jwtAudience)
            .withSubject(cleanLogin)
            .withClaim("estoqueUser", cleanLogin)
            .withExpiresAt(Date.from(expiresAt))
            .sign(Algorithm.HMAC256(config.jwtSecret))

        return EstoqueLoginResponse(
            token = token,
            usuarioEstoque = cleanLogin,
            expiraEmHoras = config.jwtExpirationHours,
            modoSimulacaoRm = config.rmMockMode
        )
    }

    suspend fun listarMovimentos1110Pendentes(): List<PendingMovementDto> {
        return rmGateway.listPending1110Movements(config.rmCodColigada)
    }

    suspend fun listarItensMovimento1110(idMov: Int): List<MovementItemDto> {
        if (idMov <= 0) throw BusinessValidationException("IDMOV inválido: $idMov")
        return rmGateway.listMovement1110Items(config.rmCodColigada, idMov)
    }

    /**
     * Efetua a baixa de retirada 100% total do movimento 1.1.10:
     * 1. Exige confirmação de 100% dos itens (sem baixa parcial).
     * 2. Autentica o login e a senha do usuário retirante diretamente no TOTVS RM.
     * 3. Salva retirado_por, data_retirada e hora_retirada na TMOVCOMPL do movimento 1.1.10.
     */
    suspend fun confirmarBaixaRetirada1110(
        idMov: Int,
        request: ConfirmWithdrawalRequest
    ): ConfirmWithdrawalResponse {
        if (idMov <= 0) {
            throw BusinessValidationException("IDMOV inválido.")
        }
        if (!request.confirmado100Porcento) {
            throw BusinessValidationException(
                "Regra Tecal: A baixa do movimento 1.1.10 só é permitida se 100% dos itens forem retirados."
            )
        }
        val cleanLogin = request.login.trim()
        if (cleanLogin.isEmpty() || request.senha.isEmpty()) {
            throw BusinessValidationException("Informe seu login e senha do TOTVS RM para confirmar a retirada.")
        }

        val idempotencyKey = request.idempotencyKey.trim()
        if (idempotencyKey.isNotEmpty()) {
            idempotencyCache[idempotencyKey]?.let { return it }
        }

        // Valida login e senha do retirante no TOTVS RM ANTES de adquirir o lock de gravação
        val withdrawerAuthenticated = rmGateway.authenticateRmUser(cleanLogin, request.senha)
        if (!withdrawerAuthenticated) {
            throw InvalidRmCredentialsException(
                message = "Login ou senha inválidos no TOTVS RM. A baixa não foi realizada.",
                isWithdrawalConfirmation = true
            )
        }

        val mutex = movementLocks.computeIfAbsent(idMov) { Mutex() }
        return mutex.withLock {
            if (idempotencyKey.isNotEmpty()) {
                idempotencyCache[idempotencyKey]?.let { return@withLock it }
            }

            val now = ZonedDateTime.now(config.timezone)
            val dataRetirada = now.format(dateFormatter)
            val horaRetirada = now.format(timeFormatter)

            val numeroMov = rmGateway.saveWithdrawalComplementaryFields(
                codColigada = config.rmCodColigada,
                idMov = idMov,
                retiradoPor = cleanLogin,
                dataRetirada = dataRetirada,
                horaRetirada = horaRetirada
            )

            val response = ConfirmWithdrawalResponse(
                codColigada = config.rmCodColigada,
                idMov = idMov,
                numeroMov = numeroMov,
                retiradoPor = cleanLogin,
                dataRetirada = dataRetirada,
                horaRetirada = horaRetirada,
                mensagem = "Baixa de retirada do movimento 1.1.10 Nº $numeroMov registrada com sucesso no TOTVS RM."
            )

            if (idempotencyKey.isNotEmpty()) {
                if (idempotencyCache.size > 2_000) {
                    idempotencyCache.clear()
                }
                idempotencyCache[idempotencyKey] = response
            }
            response
        }
    }
}

