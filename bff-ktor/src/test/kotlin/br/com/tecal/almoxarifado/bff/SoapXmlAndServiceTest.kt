package br.com.tecal.almoxarifado.bff

import br.com.tecal.almoxarifado.bff.config.AppConfig
import br.com.tecal.almoxarifado.bff.domain.BusinessValidationException
import br.com.tecal.almoxarifado.bff.domain.ConfirmWithdrawalRequest
import br.com.tecal.almoxarifado.bff.domain.InvalidRmCredentialsException
import br.com.tecal.almoxarifado.bff.domain.MovementAlreadyWithdrawnException
import br.com.tecal.almoxarifado.bff.rm.MockTotvsRmClient
import br.com.tecal.almoxarifado.bff.rm.SoapXmlPayloads
import br.com.tecal.almoxarifado.bff.service.WithdrawalService
import kotlinx.coroutines.runBlocking
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoapXmlAndServiceTest {

    private fun testConfig() = AppConfig(
        port = 8080,
        timezone = ZoneId.of("America/Sao_Paulo"),
        jwtSecret = "test-secret",
        jwtIssuer = "test-issuer",
        jwtAudience = "test-audience",
        jwtExpirationHours = 24,
        rmMockMode = true,
        rmBaseUrl = "http://localhost:8051",
        rmCodColigada = 1,
        rmCodSistemaSql = "T",
        rmContextExtra = "CODFILIAL=1;CODSISTEMA=T",
        rmServiceUser = "mestre",
        rmServicePassword = "123",
        rmSentencaPendentes = "TECAL.ALM.1110_PENDENTES",
        rmSentencaItens = "TECAL.ALM.1110_ITENS",
        colRetiradoPor = "RETIRADO_POR",
        colDataRetirada = "DATA_RETIRADA",
        colHoraRetirada = "HORA_RETIRADA",
        dataRetiradaPattern = "yyyy-MM-dd"
    )

    @Test
    fun `deve injetar retirado_por data_retirada e hora_retirada na TMOVCOMPL preservando TMOV e TITMMOV`() {
        val sampleRmXml = """
            <MovMovimento>
              <TMOV>
                <CODCOLIGADA>1</CODCOLIGADA>
                <IDMOV>45812</IDMOV>
                <NUMEROMOV>009418</NUMEROMOV>
                <CODTMV>1.1.10</CODTMV>
              </TMOV>
              <TITMMOV>
                <CODCOLIGADA>1</CODCOLIGADA>
                <IDMOV>45812</IDMOV>
                <NSEQITMMOV>1</NSEQITMMOV>
                <IDPRD>1042</IDPRD>
                <QUANTIDADE>10.0000</QUANTIDADE>
              </TITMMOV>
              <TMOVCOMPL>
                <CODCOLIGADA>1</CODCOLIGADA>
                <IDMOV>45812</IDMOV>
              </TMOVCOMPL>
            </MovMovimento>
        """.trimIndent()

        val (updatedXml, numeroMov) = SoapXmlPayloads.injectTmovComplWithdrawalFields(
            movementXml = sampleRmXml,
            codColigada = 1,
            idMov = 45812,
            colRetiradoPor = "RETIRADO_POR",
            colDataRetirada = "DATA_RETIRADA",
            colHoraRetirada = "HORA_RETIRADA",
            retiradoPorValue = "carlos.silva",
            dataRetiradaValue = "2026-10-30",
            horaRetiradaValue = "08:45:12"
        )

        assertEquals("009418", numeroMov)
        assertTrue(updatedXml.contains("<RETIRADO_POR>carlos.silva</RETIRADO_POR>"))
        assertTrue(updatedXml.contains("<DATA_RETIRADA>2026-10-30</DATA_RETIRADA>"))
        assertTrue(updatedXml.contains("<HORA_RETIRADA>08:45:12</HORA_RETIRADA>"))
        assertTrue(updatedXml.contains("<CODTMV>1.1.10</CODTMV>"))
        assertTrue(updatedXml.contains("<QUANTIDADE>10.0000</QUANTIDADE>"))

        // Tentar dar baixa uma segunda vez no mesmo XML já preenchido deve lançar conflito
        assertFailsWith<MovementAlreadyWithdrawnException> {
            SoapXmlPayloads.injectTmovComplWithdrawalFields(
                movementXml = updatedXml,
                codColigada = 1,
                idMov = 45812,
                colRetiradoPor = "RETIRADO_POR",
                colDataRetirada = "DATA_RETIRADA",
                colHoraRetirada = "HORA_RETIRADA",
                retiradoPorValue = "outro.usuario",
                dataRetiradaValue = "2026-10-30",
                horaRetiradaValue = "09:00:00"
            )
        }
    }

    @Test
    fun `fluxo completo de listagem e baixa 100 por cento remove movimento 1110 da lista pendente`() = runBlocking {
        val service = WithdrawalService(testConfig(), MockTotvsRmClient(1))

        val initialList = service.listarMovimentos1110Pendentes()
        assertEquals(3, initialList.size)
        val targetIdMov = initialList.first().idMov

        // Recusa se confirmado100Porcento for falso
        assertFailsWith<BusinessValidationException> {
            service.confirmarBaixaRetirada1110(
                idMov = targetIdMov,
                request = ConfirmWithdrawalRequest(
                    login = "joao.souza",
                    senha = "tecal123",
                    confirmado100Porcento = false,
                    idempotencyKey = "key-fail"
                )
            )
        }

        // Recusa senha incorreta com isWithdrawalConfirmation = true (sem derrubar sessão do estoque)
        val authEx = assertFailsWith<InvalidRmCredentialsException> {
            service.confirmarBaixaRetirada1110(
                idMov = targetIdMov,
                request = ConfirmWithdrawalRequest(
                    login = "joao.souza",
                    senha = "senha-errada",
                    confirmado100Porcento = true,
                    idempotencyKey = "key-wrong-pass"
                )
            )
        }
        assertTrue(authEx.isWithdrawalConfirmation)

        // Confirma com sucesso e remove da fila pendente
        val confirmation = service.confirmarBaixaRetirada1110(
            idMov = targetIdMov,
            request = ConfirmWithdrawalRequest(
                login = "joao.souza",
                senha = "tecal123",
                confirmado100Porcento = true,
                idempotencyKey = "uuid-retirada-001"
            )
        )
        assertEquals("joao.souza", confirmation.retiradoPor)

        val updatedList = service.listarMovimentos1110Pendentes()
        assertEquals(2, updatedList.size)
        assertFalse(updatedList.any { it.idMov == targetIdMov })

        // Reenvio com mesma idempotencyKey retorna exatamente a mesma resposta sem erro 409
        val replay = service.confirmarBaixaRetirada1110(
            idMov = targetIdMov,
            request = ConfirmWithdrawalRequest(
                login = "joao.souza",
                senha = "tecal123",
                confirmado100Porcento = true,
                idempotencyKey = "uuid-retirada-001"
            )
        )
        assertEquals(confirmation, replay)
    }
}

