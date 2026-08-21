@file:Suppress("InvalidPackageDeclaration", "MagicNumber")

package org.jetbrains.exposed.samples.broker.r2dbc.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.samples.broker.r2dbc.model.InstrumentType
import org.jetbrains.exposed.samples.broker.r2dbc.model.TradeType
import org.jetbrains.exposed.samples.broker.r2dbc.model.entities.*
import org.jetbrains.exposed.v1.r2dbc.SizedCollection
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import kotlin.time.Clock

@Suppress("LongMethod")
fun Application.seedRoutes() {
    routing {
        post("/seed") {
            suspendTransaction {
                // `newSuspend { }` flushes immediately, so it costs one INSERT per entity — that is the
                // right default, and every other creation below uses it.
                //
                // These four tags are independent rows with no references, so they are a good fit for
                // the non-suspending `new { }`: it only schedules the insert, and the four pending rows
                // then go out as a single batched INSERT. The next `newSuspend { }` below flushes them,
                // which is also where the tags get their ids — nothing reads those before that point.
                val (tagTech, tagFinance, tagEnergy, tagIndex) =
                    listOf("tech", "finance", "energy", "index")
                        .map { tagName ->
                            Tag.new {
                                name = tagName
                            }
                        }

                val aapl = Instrument.newSuspend {
                    ticker = "AAPL"
                    name = "Apple Inc."
                    type = InstrumentType.STOCK
                }
                val googl = Instrument.newSuspend {
                    ticker = "GOOGL"
                    name = "Alphabet Inc."
                    type = InstrumentType.STOCK
                }
                val tsla = Instrument.newSuspend {
                    ticker = "TSLA"
                    name = "Tesla Inc."
                    type = InstrumentType.STOCK
                }
                val spy = Instrument.newSuspend {
                    ticker = "SPY"
                    name = "S&P 500 ETF"
                    type = InstrumentType.ETF
                }
                val bnd = Instrument.newSuspend {
                    ticker = "BND"
                    name = "Total Bond Market ETF"
                    type = InstrumentType.BOND
                }
                val xom = Instrument.newSuspend {
                    ticker = "XOM"
                    name = "Exxon Mobil"
                    type = InstrumentType.STOCK
                }

                aapl.tags = SizedCollection(listOf(tagTech))
                googl.tags = SizedCollection(listOf(tagTech))
                tsla.tags = SizedCollection(listOf(tagTech, tagEnergy))
                spy.tags = SizedCollection(listOf(tagIndex, tagFinance))
                bnd.tags = SizedCollection(listOf(tagFinance))
                xom.tags = SizedCollection(listOf(tagEnergy))

                val brokerA = Broker.newSuspend {
                    name = "Alpha Securities"
                    licenseNumber = "SEC-001"
                }
                val brokerB = Broker.newSuspend {
                    name = "Beta Trading"
                    licenseNumber = "SEC-002"
                }

                val alice = Client.newSuspend {
                    name = "Alice Johnson"
                    email = "alice@example.com"
                    broker.set(brokerA)
                }
                val bob = Client.newSuspend {
                    name = "Bob Smith"
                    email = "bob@example.com"
                    broker.set(brokerA)
                }
                val carol = Client.newSuspend {
                    name = "Carol White"
                    email = "carol@example.com"
                    broker.set(brokerB)
                }
                val dave = Client.newSuspend {
                    name = "Dave Brown"
                    email = "dave@example.com"
                    broker.set(brokerB)
                }

                val aliceGrowth = Portfolio.newSuspend {
                    name = "Growth Portfolio"
                    client.set(alice)
                    createdAt = Clock.System.now()
                }
                val aliceSafe = Portfolio.newSuspend {
                    name = "Conservative Portfolio"
                    client.set(alice)
                    createdAt = Clock.System.now()
                }
                val bobMain = Portfolio.newSuspend {
                    name = "Main Portfolio"
                    client.set(bob)
                    createdAt = Clock.System.now()
                }
                val carolTech = Portfolio.newSuspend {
                    name = "Tech Portfolio"
                    client.set(carol)
                    createdAt = Clock.System.now()
                }

                val now = Clock.System.now()
                Trade.newSuspend {
                    client.set(alice)
                    instrument.set(aapl)
                    portfolio.set(aliceGrowth)
                    type = TradeType.BUY
                    quantity = 100
                    price = "178.50".toBigDecimal()
                    executedAt = now
                }
                Trade.newSuspend {
                    client.set(alice)
                    instrument.set(tsla)
                    portfolio.set(aliceGrowth)
                    type = TradeType.BUY
                    quantity = 50
                    price = "242.00".toBigDecimal()
                    executedAt = now
                }
                Trade.newSuspend {
                    client.set(alice)
                    instrument.set(bnd)
                    portfolio.set(aliceSafe)
                    type = TradeType.BUY
                    quantity = 200
                    price = "72.30".toBigDecimal()
                    executedAt = now
                }
                Trade.newSuspend {
                    client.set(bob)
                    instrument.set(spy)
                    portfolio.set(bobMain)
                    type = TradeType.BUY
                    quantity = 150
                    price = "450.00".toBigDecimal()
                    executedAt = now
                }
                Trade.newSuspend {
                    client.set(carol)
                    instrument.set(googl)
                    portfolio.set(carolTech)
                    type = TradeType.BUY
                    quantity = 30
                    price = "141.80".toBigDecimal()
                    executedAt = now
                }
                Trade.newSuspend {
                    client.set(dave)
                    instrument.set(xom)
                    portfolio.set(null)
                    type = TradeType.BUY
                    quantity = 75
                    price = "105.20".toBigDecimal()
                    executedAt = now
                }
            }

            call.respond(HttpStatusCode.Created, mapOf("status" to "Seed data created"))
        }
    }
}
