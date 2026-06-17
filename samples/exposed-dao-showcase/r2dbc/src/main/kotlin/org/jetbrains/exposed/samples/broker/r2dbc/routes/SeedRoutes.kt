@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.time.Clock
import org.jetbrains.exposed.r2dbc.dao.flushCache
import org.jetbrains.exposed.samples.broker.r2dbc.model.InstrumentType
import org.jetbrains.exposed.samples.broker.r2dbc.model.TradeType
import org.jetbrains.exposed.samples.broker.r2dbc.model.entities.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

fun Application.seedRoutes() {
    routing {
        post("/seed") {
            suspendTransaction {
                val tagTech = Tag.new { name = "tech" }
                val tagFinance = Tag.new { name = "finance" }
                val tagEnergy = Tag.new { name = "energy" }
                val tagIndex = Tag.new { name = "index" }
                flushCache()

                val aapl = Instrument.new { ticker = "AAPL"; name = "Apple Inc."; type = InstrumentType.STOCK }
                val googl = Instrument.new { ticker = "GOOGL"; name = "Alphabet Inc."; type = InstrumentType.STOCK }
                val tsla = Instrument.new { ticker = "TSLA"; name = "Tesla Inc."; type = InstrumentType.STOCK }
                val spy = Instrument.new { ticker = "SPY"; name = "S&P 500 ETF"; type = InstrumentType.ETF }
                val bnd = Instrument.new { ticker = "BND"; name = "Total Bond Market ETF"; type = InstrumentType.BOND }
                val xom = Instrument.new { ticker = "XOM"; name = "Exxon Mobil"; type = InstrumentType.STOCK }
                flushCache()

                aapl.tags set listOf(tagTech)
                googl.tags set listOf(tagTech)
                tsla.tags set listOf(tagTech, tagEnergy)
                spy.tags set listOf(tagIndex, tagFinance)
                bnd.tags set listOf(tagFinance)
                xom.tags set listOf(tagEnergy)
                flushCache()

                val brokerA = Broker.new { name = "Alpha Securities"; licenseNumber = "SEC-001" }
                val brokerB = Broker.new { name = "Beta Trading"; licenseNumber = "SEC-002" }
                flushCache()

                val alice = Client.new { name = "Alice Johnson"; email = "alice@example.com"; broker set brokerA }
                val bob = Client.new { name = "Bob Smith"; email = "bob@example.com"; broker set brokerA }
                val carol = Client.new { name = "Carol White"; email = "carol@example.com"; broker set brokerB }
                val dave = Client.new { name = "Dave Brown"; email = "dave@example.com"; broker set brokerB }
                flushCache()

                val aliceGrowth = Portfolio.new { name = "Growth Portfolio"; client set alice; createdAt = Clock.System.now() }
                val aliceSafe = Portfolio.new { name = "Conservative Portfolio"; client set alice; createdAt = Clock.System.now() }
                val bobMain = Portfolio.new { name = "Main Portfolio"; client set bob; createdAt = Clock.System.now() }
                val carolTech = Portfolio.new { name = "Tech Portfolio"; client set carol; createdAt = Clock.System.now() }
                flushCache()

                val now = Clock.System.now()
                Trade.new { client set alice; instrument set aapl; portfolio set aliceGrowth; type = TradeType.BUY; quantity = 100; price = "178.50".toBigDecimal(); executedAt = now }
                Trade.new { client set alice; instrument set tsla; portfolio set aliceGrowth; type = TradeType.BUY; quantity = 50; price = "242.00".toBigDecimal(); executedAt = now }
                Trade.new { client set alice; instrument set bnd; portfolio set aliceSafe; type = TradeType.BUY; quantity = 200; price = "72.30".toBigDecimal(); executedAt = now }
                Trade.new { client set bob; instrument set spy; portfolio set bobMain; type = TradeType.BUY; quantity = 150; price = "450.00".toBigDecimal(); executedAt = now }
                Trade.new { client set carol; instrument set googl; portfolio set carolTech; type = TradeType.BUY; quantity = 30; price = "141.80".toBigDecimal(); executedAt = now }
                Trade.new { client set dave; instrument set xom; portfolio set null; type = TradeType.BUY; quantity = 75; price = "105.20".toBigDecimal(); executedAt = now }
                flushCache()
            }

            call.respond(HttpStatusCode.Created, mapOf("status" to "Seed data created"))
        }
    }
}
