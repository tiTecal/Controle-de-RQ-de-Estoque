package br.com.tecal.almoxarifado.bff.rm

import br.com.tecal.almoxarifado.bff.config.AppConfig
import br.com.tecal.almoxarifado.bff.domain.MovementItemDto
import br.com.tecal.almoxarifado.bff.domain.PendingMovementDto
import br.com.tecal.almoxarifado.bff.domain.TotvsRmIntegrationException
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.Base64

class TotvsRmSoapClient(
    private val config: AppConfig,
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 8_000
            requestTimeoutMillis = 20_000
            socketTimeoutMillis = 20_000
        }
    }
) : TotvsRmGateway {

    private val logger = LoggerFactory.getLogger(TotvsRmSoapClient::class.java)

    private val wsDataServerUrl: String
        get() = "${config.rmBaseUrl}/wsDataServer/IwsDataServer"

    private val wsConsultaSqlUrl: String
        get() = "${config.rmBaseUrl}/wsConsultaSQL/IwsConsultaSQL"

    companion object {
        private const val MOVIMENTO_DATASERVER = "MovMovimentoTBCData"
        private const val ACTION_AUTENTICA = "http://www.totvs.com/IwsDataServer/AutenticaAcesso"
        private const val ACTION_READ_RECORD = "http://www.totvs.com/IwsDataServer/ReadRecord"
        private const val ACTION_SAVE_RECORD = "http://www.totvs.com/IwsDataServer/SaveRecord"
        private const val ACTION_CONSULTA_SQL = "http://www.totvs.com/IwsConsultaSQL/RealizarConsultaSQL"
    }

    override suspend fun authenticateRmUser(login: String, senha: String): Boolean {
        if (login.isBlank() || senha.isEmpty()) return false
        val envelope = SoapXmlPayloads.buildAutenticaAcessoEnvelope()

        val response = try {
            httpClient.post(wsDataServerUrl) {
                header(HttpHeaders.Authorization, basicAuthHeader(login.trim(), senha))
                header("SOAPAction", "\"$ACTION_AUTENTICA\"")
                contentType(ContentType.Text.Xml.withParameter("charset", "utf-8"))
                setBody(envelope)
            }
        } catch (ex: Exception) {
            logger.error("Erro de comunicação ao autenticar usuário '{}' no TOTVS RM Host", login, ex)
            throw TotvsRmIntegrationException(
                "Não foi possível conectar ao servidor TOTVS RM (${config.rmBaseUrl}) para validar login.",
                ex
            )
        }

        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
            return false
        }
        val body = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            logger.warn("AutenticaAcesso retornou status HTTP {} para usuário '{}'", response.status, login)
            return false
        }
        return SoapXmlPayloads.checkAutenticaAcessoSuccess(body)
    }

    override suspend fun listPending1110Movements(codColigada: Int): List<PendingMovementDto> {
        val envelope = SoapXmlPayloads.buildRealizarConsultaSqlEnvelope(
            codSentenca = config.rmSentencaPendentes,
            codColigada = codColigada,
            codSistema = config.rmCodSistemaSql,
            parameters = "CODCOLIGADA=$codColigada"
        )
        val responseXml = postSoapWithServiceAccount(wsConsultaSqlUrl, ACTION_CONSULTA_SQL, envelope)
        val rows = SoapXmlPayloads.parseConsultaSqlRows(responseXml)

        return rows.mapNotNull { row ->
            val idMov = row["IDMOV"]?.toIntOrNull() ?: return@mapNotNull null
            PendingMovementDto(
                codColigada = row["CODCOLIGADA"]?.toIntOrNull() ?: codColigada,
                idMov = idMov,
                numeroMov = row["NUMEROMOV"].orEmpty().ifBlank { idMov.toString() },
                codTmv = row["CODTMV"].orEmpty().ifBlank { "1.1.10" },
                codFilial = row["CODFILIAL"]?.toIntOrNull() ?: 1,
                codLoc = row["CODLOC"].orEmpty(),
                nomeLocal = row["NOMELOCAL"].orEmpty().ifBlank { "Almoxarifado Principal" },
                dataEmissao = row["DATAEMISSAO"].orEmpty(),
                usuarioEstoquista = row["USUARIOESTOQUISTA"].orEmpty(),
                codCCusto = row["CODCCUSTO"].orEmpty(),
                nomeCCusto = row["NOMECCUSTO"].orEmpty(),
                numeroRequisicao1113 = row["NUMEROREQUISICAO1113"].orEmpty(),
                solicitante = row["SOLICITANTE"].orEmpty(),
                qtdItens = row["QTDITENS"]?.toIntOrNull() ?: 0
            )
        }
    }

    override suspend fun listMovement1110Items(codColigada: Int, idMov: Int): List<MovementItemDto> {
        val envelope = SoapXmlPayloads.buildRealizarConsultaSqlEnvelope(
            codSentenca = config.rmSentencaItens,
            codColigada = codColigada,
            codSistema = config.rmCodSistemaSql,
            parameters = "CODCOLIGADA=$codColigada;IDMOV=$idMov"
        )
        val responseXml = postSoapWithServiceAccount(wsConsultaSqlUrl, ACTION_CONSULTA_SQL, envelope)
        val rows = SoapXmlPayloads.parseConsultaSqlRows(responseXml)

        return rows.mapIndexed { idx, row ->
            MovementItemDto(
                codColigada = row["CODCOLIGADA"]?.toIntOrNull() ?: codColigada,
                idMov = row["IDMOV"]?.toIntOrNull() ?: idMov,
                nSeqItmMov = row["NSEQITMMOV"]?.toIntOrNull() ?: (idx + 1),
                idPrd = row["IDPRD"]?.toIntOrNull() ?: 0,
                codigoPrd = row["CODIGOPRD"].orEmpty(),
                descricaoProduto = row["DESCRICAOPRODUTO"].orEmpty(),
                codUnd = row["CODUND"].orEmpty().ifBlank { "UN" },
                quantidade = row["QUANTIDADE"]?.replace(',', '.')?.toDoubleOrNull() ?: 0.0,
                codigoBarra = row["CODIGOBARRA"].orEmpty(),
                observacaoItem = row["OBSERVACAOITEM"].orEmpty()
            )
        }
    }

    override suspend fun saveWithdrawalComplementaryFields(
        codColigada: Int,
        idMov: Int,
        retiradoPor: String,
        dataRetirada: String,
        horaRetirada: String
    ): String {
        val primaryKey = "$codColigada;$idMov"

        // 1. Lê o registro atual do movimento 1.1.10 via MovMovimentoTBCData
        val readEnvelope = SoapXmlPayloads.buildReadRecordEnvelope(
            dataServerName = MOVIMENTO_DATASERVER,
            primaryKey = primaryKey,
            contexto = config.rmContextoDataServer
        )
        val readResponseXml = postSoapWithServiceAccount(wsDataServerUrl, ACTION_READ_RECORD, readEnvelope)
        val currentMovementXml = SoapXmlPayloads.extractReadRecordXml(readResponseXml, idMov)

        // 2. Injeta cirurgicamente retirado_por, data_retirada e hora_retirada na TMOVCOMPL
        val (updatedMovementXml, numeroMov) = SoapXmlPayloads.injectTmovComplWithdrawalFields(
            movementXml = currentMovementXml,
            codColigada = codColigada,
            idMov = idMov,
            colRetiradoPor = config.colRetiradoPor,
            colDataRetirada = config.colDataRetirada,
            colHoraRetirada = config.colHoraRetirada,
            retiradoPorValue = retiradoPor,
            dataRetiradaValue = dataRetirada,
            horaRetiradaValue = horaRetirada
        )

        // 3. Salva o movimento atualizado via SaveRecord no MovMovimentoTBCData
        val saveEnvelope = SoapXmlPayloads.buildSaveRecordEnvelope(
            dataServerName = MOVIMENTO_DATASERVER,
            recordXml = updatedMovementXml,
            contexto = config.rmContextoDataServer
        )
        val saveResponseXml = postSoapWithServiceAccount(wsDataServerUrl, ACTION_SAVE_RECORD, saveEnvelope)
        SoapXmlPayloads.verifySaveRecordSuccess(saveResponseXml, codColigada, idMov)

        logger.info(
            "Baixa de retirada registrada no TOTVS RM: Mov 1.1.10 IDMOV={} (Nº {}) | {}={} | {}={} | {}={}",
            idMov, numeroMov,
            config.colRetiradoPor, retiradoPor,
            config.colDataRetirada, dataRetirada,
            config.colHoraRetirada, horaRetirada
        )
        return numeroMov
    }

    private suspend fun postSoapWithServiceAccount(url: String, soapAction: String, envelopeXml: String): String {
        val response = try {
            httpClient.post(url) {
                header(
                    HttpHeaders.Authorization,
                    basicAuthHeader(config.rmServiceUser, config.rmServicePassword)
                )
                header("SOAPAction", "\"$soapAction\"")
                contentType(ContentType.Text.Xml.withParameter("charset", "utf-8"))
                setBody(envelopeXml)
            }
        } catch (ex: Exception) {
            logger.error("Falha de rede na chamada SOAP [{}] em {}", soapAction, url, ex)
            throw TotvsRmIntegrationException(
                "Falha de conexão com o servidor TOTVS RM em $url.",
                ex
            )
        }

        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
            throw TotvsRmIntegrationException(
                "Conta de serviço '${config.rmServiceUser}' sem autorização no TOTVS RM (${response.status})."
            )
        }
        return response.bodyAsText()
    }

    private fun basicAuthHeader(user: String, pass: String): String {
        val raw = "$user:$pass".toByteArray(StandardCharsets.UTF_8)
        return "Basic ${Base64.getEncoder().encodeToString(raw)}"
    }
}

