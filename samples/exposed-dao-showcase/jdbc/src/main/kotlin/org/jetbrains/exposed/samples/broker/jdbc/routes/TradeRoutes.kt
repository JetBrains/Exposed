@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.jdbc.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.time.Clock
import org.jetbrains.exposed.samples.broker.jdbc.model.dto.*
import org.jetbrains.exposed.samples.broker.jdbc.model.entities.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.tradeRoutes() {
    routing {
        route("/trades") {
            post {
                val dto = call.receive<TradeRequestDTO>()
                val result = transaction {
                    val client = Client.findById(dto.clientId)
                        ?: error("Client ${dto.clientId} not found")
                    val instrument = Instrument.findById(dto.instrumentId)
                        ?: error("Instrument ${dto.instrumentId} not found")
                    val portfolio = dto.portfolioId?.let {
                        Portfolio.findById(it) ?: error("Portfolio $it not found")
                    }

                    val trade = Trade.new {
                        this.client = client
                        this.instrument = instrument
                        this.portfolio = portfolio
                        this.type = dto.type
                        this.quantity = dto.quantity
                        this.price = dto.price.toBigDecimal()
                        this.executedAt = Clock.System.now()
                    }

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
                call.respond(HttpStatusCode.Created, result)
            }
        }
    }
}
