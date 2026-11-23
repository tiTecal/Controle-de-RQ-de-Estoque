package br.com.tecal.almoxarifado.bff.rm

import br.com.tecal.almoxarifado.bff.domain.MovementAlreadyWithdrawnException
import br.com.tecal.almoxarifado.bff.domain.MovementItemDto
import br.com.tecal.almoxarifado.bff.domain.MovementNotFoundException
import br.com.tecal.almoxarifado.bff.domain.PendingMovementDto
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Simulador realista em memória do TOTVS RM da Tecal para homologação e testes do tablet Android
 * antes de apontar para o RM.Host real (ativado quando RM_MOCK_MODE=true).
 *
 * Senhas aceitas no modo Mock: "tecal123" ou "rm123" para qualquer login não-vazio.
 */
class MockTotvsRmClient(defaultColigada: Int = 1) : TotvsRmGateway {

    private val logger = LoggerFactory.getLogger(MockTotvsRmClient::class.java)

    private data class MockMovementRecord(
        val header: PendingMovementDto,
        val items: List<MovementItemDto>,
        var retiradoPor: String? = null,
        var dataRetirada: String? = null,
        var horaRetirada: String? = null
    )

    private val movements = ConcurrentHashMap<Int, MockMovementRecord>()

    init {
        seedSampleTecalMovements(defaultColigada)
    }

    override suspend fun authenticateRmUser(login: String, senha: String): Boolean {
        val cleanLogin = login.trim()
        if (cleanLogin.isEmpty()) return false
        return senha == "tecal123" || senha == "rm123"
    }

    override suspend fun listPending1110Movements(codColigada: Int): List<PendingMovementDto> {
        return movements.values
            .filter { record ->
                record.header.codColigada == codColigada &&
                    (record.retiradoPor.isNullOrBlank() ||
                        record.dataRetirada.isNullOrBlank() ||
                        record.horaRetirada.isNullOrBlank())
            }
            .map { it.header }
            .sortedByDescending { it.idMov }
    }

    override suspend fun listMovement1110Items(codColigada: Int, idMov: Int): List<MovementItemDto> {
        val record = movements[idMov] ?: throw MovementNotFoundException(idMov)
        if (record.header.codColigada != codColigada) throw MovementNotFoundException(idMov)
        return record.items
    }

    override suspend fun saveWithdrawalComplementaryFields(
        codColigada: Int,
        idMov: Int,
        retiradoPor: String,
        dataRetirada: String,
        horaRetirada: String
    ): String {
        val record = movements[idMov] ?: throw MovementNotFoundException(idMov)
        if (record.header.codColigada != codColigada) throw MovementNotFoundException(idMov)

        synchronized(record) {
            if (!record.retiradoPor.isNullOrBlank() &&
                !record.dataRetirada.isNullOrBlank() &&
                !record.horaRetirada.isNullOrBlank()
            ) {
                throw MovementAlreadyWithdrawnException(
                    idMov = idMov,
                    retiradoPorExistente = record.retiradoPor.orEmpty()
                )
            }
            record.retiradoPor = retiradoPor
            record.dataRetirada = dataRetirada
            record.horaRetirada = horaRetirada
        }

        logger.info(
            "[MOCK RM] TMOVCOMPL atualizado no Mov 1.1.10 IDMOV={} (Nº {}): retirado_por={}, data_retirada={}, hora_retirada={}",
            idMov, record.header.numeroMov, retiradoPor, dataRetirada, horaRetirada
        )
        return record.header.numeroMov
    }

    private fun seedSampleTecalMovements(coligada: Int) {
        val m1 = MockMovementRecord(
            header = PendingMovementDto(
                codColigada = coligada,
                idMov = 45812,
                numeroMov = "009418",
                codTmv = "1.1.10",
                codFilial = 1,
                codLoc = "01.01",
                nomeLocal = "Almoxarifado Central Tecal",
                dataEmissao = "2026-10-30T07:50:00",
                usuarioEstoquista = "marcos.estoque",
                codCCusto = "3.02.01",
                nomeCCusto = "Manutenção Industrial - Caldeiraria",
                numeroRequisicao1113 = "014205",
                solicitante = "carlos.silva",
                qtdItens = 3
            ),
            items = listOf(
                MovementItemDto(
                    codColigada = coligada,
                    idMov = 45812,
                    nSeqItmMov = 1,
                    idPrd = 1042,
                    codigoPrd = "INS.00412",
                    descricaoProduto = "DISCO DE CORTE INOX 7\" X 1/16\" X 7/8\"",
                    codUnd = "PC",
                    quantidade = 10.0,
                    codigoBarra = "7891234500412",
                    observacaoItem = "Separado bancada 02"
                ),
                MovementItemDto(
                    codColigada = coligada,
                    idMov = 45812,
                    nSeqItmMov = 2,
                    idPrd = 1089,
                    codigoPrd = "SOL.00108",
                    descricaoProduto = "ELETRODO REVESTIDO E7018 3,25MM (LATA 5KG)",
                    codUnd = "LT",
                    quantidade = 2.0,
                    codigoBarra = "7891234500108",
                    observacaoItem = ""
                ),
                MovementItemDto(
                    codColigada = coligada,
                    idMov = 45812,
                    nSeqItmMov = 3,
                    idPrd = 2190,
                    codigoPrd = "EPI.00054",
                    descricaoProduto = "LUVA DE RASPA CANO LONGO REFORCADA",
                    codUnd = "PAR",
                    quantidade = 2.0,
                    codigoBarra = "7891234500054",
                    observacaoItem = ""
                )
            )
        )

        val m2 = MockMovementRecord(
            header = PendingMovementDto(
                codColigada = coligada,
                idMov = 45815,
                numeroMov = "009419",
                codTmv = "1.1.10",
                codFilial = 1,
                codLoc = "01.01",
                nomeLocal = "Almoxarifado Central Tecal",
                dataEmissao = "2026-10-30T08:15:00",
                usuarioEstoquista = "marcos.estoque",
                codCCusto = "4.01.05",
                nomeCCusto = "Montagem Elétrica e Automação",
                numeroRequisicao1113 = "014209",
                solicitante = "fernanda.rocha",
                qtdItens = 2
            ),
            items = listOf(
                MovementItemDto(
                    codColigada = coligada,
                    idMov = 45815,
                    nSeqItmMov = 1,
                    idPrd = 3310,
                    codigoPrd = "ELE.01240",
                    descricaoProduto = "CABO FLEXIVEL PP 4X2,5MM 1KV PRETO",
                    codUnd = "M",
                    quantidade = 50.0,
                    codigoBarra = "7891234501240",
                    observacaoItem = "Rolo cortado e etiquetado"
                ),
                MovementItemDto(
                    codColigada = coligada,
                    idMov = 45815,
                    nSeqItmMov = 2,
                    idPrd = 3402,
                    codigoPrd = "ELE.00890",
                    descricaoProduto = "DISJUNTOR TRIPOLAR DIN CURVA C 40A",
                    codUnd = "PC",
                    quantidade = 3.0,
                    codigoBarra = "7891234500890",
                    observacaoItem = ""
                )
            )
        )

        val m3 = MockMovementRecord(
            header = PendingMovementDto(
                codColigada = coligada,
                idMov = 45819,
                numeroMov = "009420",
                codTmv = "1.1.10",
                codFilial = 1,
                codLoc = "01.01",
                nomeLocal = "Almoxarifado Central Tecal",
                dataEmissao = "2026-10-30T08:30:00",
                usuarioEstoquista = "marcos.estoque",
                codCCusto = "2.04.02",
                nomeCCusto = "Operação Campo - Obra Planta Sul",
                numeroRequisicao1113 = "014212",
                solicitante = "joao.souza",
                qtdItens = 4
            ),
            items = listOf(
                MovementItemDto(
                    codColigada = coligada,
                    idMov = 45819,
                    nSeqItmMov = 1,
                    idPrd = 4100,
                    codigoPrd = "HID.00321",
                    descricaoProduto = "VALVULA ESFERA INOX 316 TRIPARTIDA 1.1/2\" BSP",
                    codUnd = "PC",
                    quantidade = 4.0,
                    codigoBarra = "7891234500321",
                    observacaoItem = ""
                ),
                MovementItemDto(
                    codColigada = coligada,
                    idMov = 45819,
                    nSeqItmMov = 2,
                    idPrd = 4125,
                    codigoPrd = "FIX.00912",
                    descricaoProduto = "ESTOJO PARAFUSO A193 B7 5/8\" X 110MM C/ 2 PORCAS 2H",
                    codUnd = "CJ",
                    quantidade = 24.0,
                    codigoBarra = "7891234500912",
                    observacaoItem = ""
                ),
                MovementItemDto(
                    codColigada = coligada,
                    idMov = 45819,
                    nSeqItmMov = 3,
                    idPrd = 4188,
                    codigoPrd = "VED.00210",
                    descricaoProduto = "JUNTA ESPIRALADA INOX/GRAFITE DN 4\" CLASSE 150#",
                    codUnd = "PC",
                    quantidade = 6.0,
                    codigoBarra = "7891234500210",
                    observacaoItem = ""
                ),
                MovementItemDto(
                    codColigada = coligada,
                    idMov = 45819,
                    nSeqItmMov = 4,
                    idPrd = 1520,
                    codigoPrd = "QUI.00045",
                    descricaoProduto = "DESENGRIPANTE SPRAY INDUSTRIAL 300ML",
                    codUnd = "FR",
                    quantidade = 3.0,
                    codigoBarra = "7891234500045",
                    observacaoItem = ""
                )
            )
        )

        movements[m1.header.idMov] = m1
        movements[m2.header.idMov] = m2
        movements[m3.header.idMov] = m3
    }
}

