@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.jdbc.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.samples.broker.jdbc.model.dto.*
import org.jetbrains.exposed.samples.broker.jdbc.model.entities.*
import org.jetbrains.exposed.v1.dao.load
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.clientRoutes() {
    routing {
        route("/clients") {
            post {
                val dto = call.receive<ClientDTO>()
                val result = transaction {
                    val broker = Broker.findById(dto.brokerId)
                        ?: error("Broker ${dto.brokerId} not found")
                    val client = Client.new {
                        name = dto.name
                        email = dto.email
                        this.broker = broker
                    }
                    ClientDTO(client.id.value, client.name, client.email, client.broker.id.value)
                }
                call.respond(HttpStatusCode.Created, result)
            }

            get("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                val detail = transaction {
                    val client = Client.findById(id)
                        ?: return@transaction null
                    client.load(Client::broker, Client::portfolios)
                    ClientDetailDTO(
                        id = client.id.value,
                        name = client.name,
                        email = client.email,
                        broker = BrokerDTO(
                            client.broker.id.value,
                            client.broker.name,
                            client.broker.licenseNumber
                        ),
                        portfolios = client.portfolios.map {
                            PortfolioSummaryDTO(it.id.value, it.name, it.createdAt.toString())
                        }
                    )
                }
                if (detail != null) call.respond(detail)
                else call.respond(HttpStatusCode.NotFound, "Client not found")
            }

            get("{id}/trades") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                val trades = transaction {
                    val client = Client.findById(id) ?: return@transaction null
                    client.trades.map { trade ->
                        TradeDetailDTO(
                            id = trade.id.value,
                            instrumentTicker = trade.instrument.ticker,
                            instrumentName = trade.instrument.name,
                            type = trade.type,
                            quantity = trade.quantity,
                            price = trade.price.toPlainString(),
                            executedAt = trade.executedAt.toString(),
                            portfolioName = trade.portfolio?.name
                        )
                    }
                }
                if (trades != null) call.respond(trades)
                else call.respond(HttpStatusCode.NotFound, "Client not found")
            }
        }
    }
}
