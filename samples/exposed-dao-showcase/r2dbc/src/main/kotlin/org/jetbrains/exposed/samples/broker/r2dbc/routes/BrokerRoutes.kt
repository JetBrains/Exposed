@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.routes

import io.ktor.http.*
import kotlinx.coroutines.flow.toList
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.r2dbc.dao.flushCache
import org.jetbrains.exposed.r2dbc.dao.relationships.load
import org.jetbrains.exposed.samples.broker.r2dbc.model.dto.*
import org.jetbrains.exposed.samples.broker.r2dbc.model.entities.*
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

fun Application.brokerRoutes() {
    routing {
        route("/brokers") {
            post {
                val dto = call.receive<BrokerDTO>()
                val result = suspendTransaction {
                    val broker = Broker.new {
                        name = dto.name
                        licenseNumber = dto.licenseNumber
                    }
                    flushCache()
                    BrokerDTO(broker.id.value, broker.name, broker.licenseNumber)
                }
                call.respond(HttpStatusCode.Created, result)
            }

            get {
                val brokers = suspendTransaction {
                    Broker.all().toList().map { broker ->
                        BrokerSummaryDTO(
                            id = broker.id.value,
                            name = broker.name,
                            licenseNumber = broker.licenseNumber,
                            clientCount = broker.clients().count()
                        )
                    }
                }
                call.respond(brokers)
            }

            get("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                val detail = suspendTransaction {
                    val broker = Broker.findById(id)
                        ?: return@suspendTransaction null
                    broker.load(Broker::clients)
                    BrokerDetailDTO(
                        id = broker.id.value,
                        name = broker.name,
                        licenseNumber = broker.licenseNumber,
                        clients = broker.clients().toList().map {
                            ClientSummaryDTO(it.id.value, it.name, it.email)
                        }
                    )
                }
                if (detail != null) call.respond(detail)
                else call.respond(HttpStatusCode.NotFound, "Broker not found")
            }
        }
    }
}
