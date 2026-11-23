package br.com.tecal.almoxarifado.bff.routes

import br.com.tecal.almoxarifado.bff.config.AppConfig
import br.com.tecal.almoxarifado.bff.domain.BusinessValidationException
import br.com.tecal.almoxarifado.bff.domain.ConfirmWithdrawalRequest
import br.com.tecal.almoxarifado.bff.domain.EstoqueLoginRequest
import br.com.tecal.almoxarifado.bff.domain.HealthResponse
import br.com.tecal.almoxarifado.bff.service.WithdrawalService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun Route.withdrawalRoutes(
    config: AppConfig,
    withdrawalService: WithdrawalService
) {
    route("/api/v1") {

        get("/health") {
            call.respond(
                HttpStatusCode.OK,
                HealthResponse(
                    status = "UP",
                    rmMockMode = config.rmMockMode,
                    codColigada = config.rmCodColigada,
                    timestampServidor = ZonedDateTime.now(config.timezone)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                )
            )
        }

        // Login inicial do usuário do estoque para manter o tablet logado na porta
        post("/auth/login") {
            val req = call.receive<EstoqueLoginRequest>()
            val res = withdrawalService.loginSessaoEstoque(req)
            call.respond(HttpStatusCode.OK, res)
        }

        // Rotas protegidas pela sessão ativa do tablet na porta do almoxarifado
        authenticate("estoque-jwt") {

            // Lista movimentos 1.1.10 sem retirado_por, data_retirada e hora_retirada preenchidos
            get("/movimentos/pendentes") {
                val pendentes = withdrawalService.listarMovimentos1110Pendentes()
                call.respond(HttpStatusCode.OK, pendentes)
            }

            // Lista 100% dos itens vinculados ao movimento 1.1.10 indicado pelo usuário do estoque
            get("/movimentos/{idMov}/itens") {
                val idMov = call.parameters["idMov"]?.toIntOrNull()
                    ?: throw BusinessValidationException("Parâmetro idMov inválido.")
                val itens = withdrawalService.listarItensMovimento1110(idMov)
                call.respond(HttpStatusCode.OK, itens)
            }

            // Dá baixa na retirada 100% do movimento 1.1.10 exigindo login e senha do usuário retirante
            post("/movimentos/{idMov}/baixar") {
                val idMov = call.parameters["idMov"]?.toIntOrNull()
                    ?: throw BusinessValidationException("Parâmetro idMov inválido.")
                val req = call.receive<ConfirmWithdrawalRequest>()
                val res = withdrawalService.confirmarBaixaRetirada1110(idMov, req)
                call.respond(HttpStatusCode.OK, res)
            }
        }
    }
}

