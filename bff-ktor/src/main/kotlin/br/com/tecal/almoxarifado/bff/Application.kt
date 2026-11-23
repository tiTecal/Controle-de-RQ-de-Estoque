package br.com.tecal.almoxarifado.bff

import br.com.tecal.almoxarifado.bff.config.AppConfig
import br.com.tecal.almoxarifado.bff.domain.ApiErrorResponse
import br.com.tecal.almoxarifado.bff.domain.BusinessValidationException
import br.com.tecal.almoxarifado.bff.domain.InvalidRmCredentialsException
import br.com.tecal.almoxarifado.bff.domain.MovementAlreadyWithdrawnException
import br.com.tecal.almoxarifado.bff.domain.MovementNotFoundException
import br.com.tecal.almoxarifado.bff.domain.TotvsRmIntegrationException
import br.com.tecal.almoxarifado.bff.rm.MockTotvsRmClient
import br.com.tecal.almoxarifado.bff.rm.TotvsRmGateway
import br.com.tecal.almoxarifado.bff.rm.TotvsRmSoapClient
import br.com.tecal.almoxarifado.bff.routes.withdrawalRoutes
import br.com.tecal.almoxarifado.bff.service.WithdrawalService
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

fun main() {
    val config = AppConfig.load()
    val logger = LoggerFactory.getLogger("TecalAlmoxarifadoBff")
    logger.info(
        "Iniciando Tecal Almoxarifado BFF na porta {} | Coligada={} | RM_MOCK_MODE={} | Colunas TMOVCOMPL=({}, {}, {})",
        config.port,
        config.rmCodColigada,
        config.rmMockMode,
        config.colRetiradoPor,
        config.colDataRetirada,
        config.colHoraRetirada
    )

    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        configureModule(config)
    }.start(wait = true)
}

fun Application.configureModule(
    config: AppConfig = AppConfig.load(),
    customGateway: TotvsRmGateway? = null
) {
    val logger = LoggerFactory.getLogger("Application")

    val rmGateway: TotvsRmGateway = customGateway ?: if (config.rmMockMode) {
        MockTotvsRmClient(config.rmCodColigada)
    } else {
        TotvsRmSoapClient(config)
    }

    val withdrawalService = WithdrawalService(config, rmGateway)

    install(CallLogging)

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = false
                isLenient = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        )
    }

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
    }

    install(Authentication) {
        jwt("estoque-jwt") {
            realm = "Tecal Almoxarifado Kiosk"
            verifier(
                JWT.require(Algorithm.HMAC256(config.jwtSecret))
                    .withIssuer(config.jwtIssuer)
                    .withAudience(config.jwtAudience)
                    .build()
            )
            validate { credential ->
                val user = credential.payload.getClaim("estoqueUser").asString()
                if (!user.isNullOrBlank()) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiErrorResponse(
                        codigo = "SESSION_EXPIRED",
                        mensagem = "Sessão do estoque expirada ou ausente. Faça login do usuário do estoque."
                    )
                )
            }
        }
    }

    install(StatusPages) {
        exception<InvalidRmCredentialsException> { call, cause ->
            if (cause.isWithdrawalConfirmation) {
                // HTTP 403 específico para não derrubar a sessão JWT do estoque no tablet!
                call.respond(
                    HttpStatusCode.Forbidden,
                    ApiErrorResponse(
                        codigo = "INVALID_WITHDRAWER_CREDENTIALS",
                        mensagem = cause.message ?: "Login ou senha do retirante inválidos no TOTVS RM."
                    )
                )
            } else {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ApiErrorResponse(
                        codigo = "INVALID_ESTOQUE_CREDENTIALS",
                        mensagem = cause.message ?: "Login ou senha do estoque inválidos no TOTVS RM."
                    )
                )
            }
        }

        exception<MovementAlreadyWithdrawnException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ApiErrorResponse(
                    codigo = "ALREADY_WITHDRAWN",
                    mensagem = cause.message ?: "Este movimento 1.1.10 já teve sua retirada registrada."
                )
            )
        }

        exception<MovementNotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                ApiErrorResponse(
                    codigo = "MOVEMENT_NOT_FOUND",
                    mensagem = cause.message ?: "Movimento 1.1.10 não encontrado."
                )
            )
        }

        exception<BusinessValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ApiErrorResponse(
                    codigo = "VALIDATION_ERROR",
                    mensagem = cause.message ?: "Dados inválidos na requisição."
                )
            )
        }

        exception<TotvsRmIntegrationException> { call, cause ->
            logger.error("Erro de integração com o TOTVS RM", cause)
            call.respond(
                HttpStatusCode.BadGateway,
                ApiErrorResponse(
                    codigo = "RM_INTEGRATION_ERROR",
                    mensagem = cause.message ?: "Erro de comunicação com o servidor TOTVS RM."
                )
            )
        }

        exception<Throwable> { call, cause ->
            logger.error("Erro interno inesperado no BFF", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiErrorResponse(
                    codigo = "INTERNAL_ERROR",
                    mensagem = "Erro interno no servidor de integração do almoxarifado."
                )
            )
        }
    }

    routing {
        withdrawalRoutes(config, withdrawalService)
    }
}

