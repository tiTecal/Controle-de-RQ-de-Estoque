package br.com.tecal.almoxarifado.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EstoqueLoginRequest(
    @SerialName("login") val login: String,
    @SerialName("senha") val senha: String
)

@Serializable
data class EstoqueLoginResponse(
    @SerialName("token") val token: String,
    @SerialName("usuarioEstoque") val usuarioEstoque: String,
    @SerialName("expiraEmHoras") val expiraEmHoras: Long,
    @SerialName("modoSimulacaoRm") val modoSimulacaoRm: Boolean = false
)

@Serializable
data class PendingMovementDto(
    @SerialName("codColigada") val codColigada: Int,
    @SerialName("idMov") val idMov: Int,
    @SerialName("numeroMov") val numeroMov: String,
    @SerialName("codTmv") val codTmv: String = "1.1.10",
    @SerialName("codFilial") val codFilial: Int = 1,
    @SerialName("codLoc") val codLoc: String = "",
    @SerialName("nomeLocal") val nomeLocal: String = "",
    @SerialName("dataEmissao") val dataEmissao: String = "",
    @SerialName("usuarioEstoquista") val usuarioEstoquista: String = "",
    @SerialName("codCCusto") val codCCusto: String = "",
    @SerialName("nomeCCusto") val nomeCCusto: String = "",
    @SerialName("numeroRequisicao1113") val numeroRequisicao1113: String = "",
    @SerialName("solicitante") val solicitante: String = "",
    @SerialName("qtdItens") val qtdItens: Int = 0,
    @SerialName("statusRm") val statusRm: String = "A",
    @SerialName("jaRetirado") val jaRetirado: Boolean = false,
    @SerialName("retiradoPor") val retiradoPor: String = "",
    @SerialName("dataRetirada") val dataRetirada: String = "",
    @SerialName("horaRetirada") val horaRetirada: String = ""
)

@Serializable
data class MovementItemDto(
    @SerialName("codColigada") val codColigada: Int,
    @SerialName("idMov") val idMov: Int,
    @SerialName("nSeqItmMov") val nSeqItmMov: Int,
    @SerialName("idPrd") val idPrd: Int = 0,
    @SerialName("codigoPrd") val codigoPrd: String,
    @SerialName("descricaoProduto") val descricaoProduto: String,
    @SerialName("codUnd") val codUnd: String,
    @SerialName("quantidade") val quantidade: Double,
    @SerialName("codigoBarra") val codigoBarra: String = "",
    @SerialName("observacaoItem") val observacaoItem: String = ""
)

@Serializable
data class ConfirmWithdrawalRequest(
    @SerialName("login") val login: String,
    @SerialName("senha") val senha: String,
    @SerialName("confirmado100Porcento") val confirmado100Porcento: Boolean,
    @SerialName("idempotencyKey") val idempotencyKey: String
)

@Serializable
data class ConfirmWithdrawalResponse(
    @SerialName("codColigada") val codColigada: Int,
    @SerialName("idMov") val idMov: Int,
    @SerialName("numeroMov") val numeroMov: String,
    @SerialName("retiradoPor") val retiradoPor: String,
    @SerialName("dataRetirada") val dataRetirada: String,
    @SerialName("horaRetirada") val horaRetirada: String,
    @SerialName("mensagem") val mensagem: String
)

@Serializable
data class AlmoxarifadoUserDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("login") val login: String,
    @SerialName("nomeCompleto") val nomeCompleto: String,
    @SerialName("cpf") val cpf: String = "",
    @SerialName("chapa") val chapa: String = "",
    @SerialName("secao") val secao: String = "",
    @SerialName("origemRm") val origemRm: Boolean = false,
    @SerialName("perfil") val perfil: String = "RETIRANTE",
    @SerialName("ativo") val ativo: Boolean = true,
    @SerialName("criadoEm") val criadoEm: String = "",
    @SerialName("atualizadoEm") val atualizadoEm: String = ""
)

@Serializable
data class LookupCpfRequest(
    @SerialName("cpf") val cpf: String
)

@Serializable
data class VerifyAdminPinRequest(
    @SerialName("pin") val pin: String
)

@Serializable
data class VerifyAdminPinResponse(
    @SerialName("valid") val valid: Boolean = false,
    @SerialName("adminToken") val adminToken: String = "",
    @SerialName("expiraEmMinutos") val expiraEmMinutos: Long = 15
)

@Serializable
data class UpsertUserRequest(
    @SerialName("login") val login: String,
    @SerialName("nomeCompleto") val nomeCompleto: String,
    @SerialName("senha") val senha: String? = null,
    @SerialName("perfil") val perfil: String = "RETIRANTE",
    @SerialName("ativo") val ativo: Boolean = true
)

@Serializable
data class UpdateUserStatusRequest(
    @SerialName("ativo") val ativo: Boolean
)

@Serializable
data class ApiErrorResponse(
    @SerialName("codigo") val codigo: String = "ERROR",
    @SerialName("mensagem") val mensagem: String = "Erro inesperado na comunicação com o servidor."
)
