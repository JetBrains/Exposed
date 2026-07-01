@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.samples.broker.r2dbc.model.dto.*
import org.jetbrains.exposed.samples.broker.r2dbc.model.entities.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlin.time.Clock

fun Application.portfolioRoutes() {
    routing {
        route("/portfolios") {
            post {
                val dto = call.receive<PortfolioDTO>()
                val result = suspendTransaction {
                    val client = Client.findById(dto.clientId)
                        ?: error("Client ${dto.clientId} not found")
                    val portfolio = Portfolio.new {
                        name = dto.name
                        this.client.set(client)
                        createdAt = Clock.System.now()
                    }.flush()
                    PortfolioDTO(portfolio.id.value, portfolio.name, portfolio.client().id.value)
                }
                call.respond(HttpStatusCode.Created, result)
            }

            get("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                val detail = suspendTransaction {
                    val portfolio = Portfolio.findById(id) ?: return@suspendTransaction null
                    PortfolioDetailDTO(
                        id = portfolio.id.value,
                        name = portfolio.name,
                        createdAt = portfolio.createdAt.toString(),
                        trades = portfolio.trades.toList().map { trade ->
                            TradeDetailDTO(
                                id = trade.id.value,
                                instrumentTicker = trade.instrument().ticker,
                                instrumentName = trade.instrument().name,
                                type = trade.type,
                                quantity = trade.quantity,
                                price = trade.price.toPlainString(),
                                executedAt = trade.executedAt.toString(),
                                portfolioName = portfolio.name
                            )
                        }
                    )
                }
                if (detail != null) {
                    call.respond(detail)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Portfolio not found")
                }
            }
        }
    }
}
