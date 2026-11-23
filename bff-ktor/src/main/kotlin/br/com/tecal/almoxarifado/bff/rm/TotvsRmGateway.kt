package br.com.tecal.almoxarifado.bff.rm

import br.com.tecal.almoxarifado.bff.domain.MovementItemDto
import br.com.tecal.almoxarifado.bff.domain.PendingMovementDto

interface TotvsRmGateway {
    /**
     * Valida login e senha nativos de um usuário do TOTVS RM (GUSUARIO).
     * Usado tanto no login inicial da sessão do estoque no tablet quanto na confirmação
     * de retirada pelo usuário que está levando 100% dos materiais do movimento 1.1.10.
     */
    suspend fun authenticateRmUser(login: String, senha: String): Boolean

    /**
     * Lista todos os movimentos 1.1.10 ativos que ainda NÃO têm os campos complementares
     * (retirado_por, data_retirada, hora_retirada) preenchidos na TMOVCOMPL.
     */
    suspend fun listPending1110Movements(codColigada: Int): List<PendingMovementDto>

    /**
     * Lista 100% dos itens vinculados ao movimento 1.1.10 selecionado pelo estoquista.
     */
    suspend fun listMovement1110Items(codColigada: Int, idMov: Int): List<MovementItemDto>

    /**
     * Salva retirado_por, data_retirada e hora_retirada nos campos complementares (TMOVCOMPL)
     * do movimento 1.1.10 via DataServer MovMovimentoTBCData.
     * Retorna o número do movimento (NUMEROMOV) atualizado.
     */
    suspend fun saveWithdrawalComplementaryFields(
        codColigada: Int,
        idMov: Int,
        retiradoPor: String,
        dataRetirada: String,
        horaRetirada: String
    ): String
}

