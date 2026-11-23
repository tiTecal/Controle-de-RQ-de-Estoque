package br.com.tecal.almoxarifado.bff.domain

import kotlinx.serialization.Serializable

@Serializable
data class EstoqueLoginRequest(
    val login: String,
    val senha: String
)

@Serializable
data class EstoqueLoginResponse(
    val token: String,
    val usuarioEstoque: String,
    val expiraEmHoras: Long,
    val modoSimulacaoRm: Boolean
)

@Serializable
data class PendingMovementDto(
    val codColigada: Int,
    val idMov: Int,
    val numeroMov: String,
    val codTmv: String,
    val codFilial: Int,
    val codLoc: String,
    val nomeLocal: String,
    val dataEmissao: String,
    val usuarioEstoquista: String,
    val codCCusto: String,
    val nomeCCusto: String,
    val numeroRequisicao1113: String,
    val solicitante: String,
    val qtdItens: Int
)

@Serializable
data class MovementItemDto(
    val codColigada: Int,
    val idMov: Int,
    val nSeqItmMov: Int,
    val idPrd: Int,
    val codigoPrd: String,
    val descricaoProduto: String,
    val codUnd: String,
    val quantidade: Double,
    val codigoBarra: String,
    val observacaoItem: String
)

@Serializable
data class ConfirmWithdrawalRequest(
    val login: String,
    val senha: String,
    val confirmado100Porcento: Boolean,
    val idempotencyKey: String
)

@Serializable
data class ConfirmWithdrawalResponse(
    val codColigada: Int,
    val idMov: Int,
    val numeroMov: String,
    val retiradoPor: String,
    val dataRetirada: String,
    val horaRetirada: String,
    val mensagem: String
)

@Serializable
data class HealthResponse(
    val status: String,
    val rmMockMode: Boolean,
    val codColigada: Int,
    val timestampServidor: String
)

@Serializable
data class ApiErrorResponse(
    val codigo: String,
    val mensagem: String
)

class InvalidRmCredentialsException(
    message: String = "Login ou senha inválidos no TOTVS RM.",
    val isWithdrawalConfirmation: Boolean = false
) : RuntimeException(message)

class MovementAlreadyWithdrawnException(
    val idMov: Int,
    val retiradoPorExistente: String
) : RuntimeException(
    "O movimento 1.1.10 (IDMOV=$idMov) já possui baixa de retirada registrada por '$retiradoPorExistente'."
)

class MovementNotFoundException(
    val idMov: Int
) : RuntimeException("Movimento 1.1.10 (IDMOV=$idMov) não encontrado no TOTVS RM.")

class BusinessValidationException(message: String) : RuntimeException(message)

class TotvsRmIntegrationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

