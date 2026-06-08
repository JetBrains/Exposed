@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.jdbc.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.time.Clock
import org.jetbrains.exposed.samples.broker.jdbc.model.InstrumentType
import org.jetbrains.exposed.samples.broker.jdbc.model.TradeType
import org.jetbrains.exposed.samples.broker.jdbc.model.entities.*
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.seedRoutes() {
    routing {
        post("/seed") {
            transaction {
                val tagTech = Tag.new { name = "tech" }
                val tagFinance = Tag.new { name = "finance" }
                val tagEnergy = Tag.new { name = "energy" }
                val tagIndex = Tag.new { name = "index" }

                val aapl = Instrument.new { ticker = "AAPL"; name = "Apple Inc."; type = InstrumentType.STOCK }
                val googl = Instrument.new { ticker = "GOOGL"; name = "Alphabet Inc."; type = InstrumentType.STOCK }
                val tsla = Instrument.new { ticker = "TSLA"; name = "Tesla Inc."; type = InstrumentType.STOCK }
                val spy = Instrument.new { ticker = "SPY"; name = "S&P 500 ETF"; type = InstrumentType.ETF }
                val bnd = Instrument.new { ticker = "BND"; name = "Total Bond Market ETF"; type = InstrumentType.BOND }
                val xom = Instrument.new { ticker = "XOM"; name = "Exxon Mobil"; type = InstrumentType.STOCK }

                aapl.tags = SizedCollection(listOf(tagTech))
                googl.tags = SizedCollection(listOf(tagTech))
                tsla.tags = SizedCollection(listOf(tagTech, tagEnergy))
                spy.tags = SizedCollection(listOf(tagIndex, tagFinance))
                bnd.tags = SizedCollection(listOf(tagFinance))
                xom.tags = SizedCollection(listOf(tagEnergy))

                val brokerA = Broker.new { name = "Alpha Securities"; licenseNumber = "SEC-001" }
                val brokerB = Broker.new { name = "Beta Trading"; licenseNumber = "SEC-002" }

                val alice = Client.new { name = "Alice Johnson"; email = "alice@example.com"; broker = brokerA }
                val bob = Client.new { name = "Bob Smith"; email = "bob@example.com"; broker = brokerA }
                val carol = Client.new { name = "Carol White"; email = "carol@example.com"; broker = brokerB }
                val dave = Client.new { name = "Dave Brown"; email = "dave@example.com"; broker = brokerB }

                val aliceGrowth = Portfolio.new { name = "Growth Portfolio"; client = alice; createdAt = Clock.System.now() }
                val aliceSafe = Portfolio.new { name = "Conservative Portfolio"; client = alice; createdAt = Clock.System.now() }
                val bobMain = Portfolio.new { name = "Main Portfolio"; client = bob; createdAt = Clock.System.now() }
                val carolTech = Portfolio.new { name = "Tech Portfolio"; client = carol; createdAt = Clock.System.now() }

                val now = Clock.System.now()
                Trade.new { client = alice; instrument = aapl; portfolio = aliceGrowth; type = TradeType.BUY; quantity = 100; price = "178.50".toBigDecimal(); executedAt = now }
                Trade.new { client = alice; instrument = tsla; portfolio = aliceGrowth; type = TradeType.BUY; quantity = 50; price = "242.00".toBigDecimal(); executedAt = now }
                Trade.new { client = alice; instrument = bnd; portfolio = aliceSafe; type = TradeType.BUY; quantity = 200; price = "72.30".toBigDecimal(); executedAt = now }
                Trade.new { client = bob; instrument = spy; portfolio = bobMain; type = TradeType.BUY; quantity = 150; price = "450.00".toBigDecimal(); executedAt = now }
                Trade.new { client = carol; instrument = googl; portfolio = carolTech; type = TradeType.BUY; quantity = 30; price = "141.80".toBigDecimal(); executedAt = now }
                Trade.new { client = dave; instrument = xom; portfolio = null; type = TradeType.BUY; quantity = 75; price = "105.20".toBigDecimal(); executedAt = now }
            }

            call.respond(HttpStatusCode.Created, mapOf("status" to "Seed data created"))
        }
    }
}
