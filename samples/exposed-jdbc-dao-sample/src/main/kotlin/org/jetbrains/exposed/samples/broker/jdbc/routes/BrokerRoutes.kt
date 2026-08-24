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

fun Application.brokerRoutes() {
    routing {
        route("/brokers") {
            post {
                val dto = call.receive<BrokerDTO>()
                val result = transaction {
                    val broker = Broker.new {
                        name = dto.name
                        licenseNumber = dto.licenseNumber
                    }
                    BrokerDTO(broker.id.value, broker.name, broker.licenseNumber)
                }
                call.respond(HttpStatusCode.Created, result)
            }

            get {
                val brokers = transaction {
                    Broker.all().map { broker ->
                        BrokerSummaryDTO(
                            id = broker.id.value,
                            name = broker.name,
                            licenseNumber = broker.licenseNumber,
                            clientCount = broker.clients.count()
                        )
                    }
                }
                call.respond(brokers)
            }

            get("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                val detail = transaction {
                    val broker = Broker.findById(id)
                        ?: return@transaction null
                    broker.load(Broker::clients)
                    BrokerDetailDTO(
                        id = broker.id.value,
                        name = broker.name,
                        licenseNumber = broker.licenseNumber,
                        clients = broker.clients.map {
                            ClientSummaryDTO(it.id.value, it.name, it.email)
                        }
                    )
                }
                if (detail != null) {
                    call.respond(detail)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Broker not found")
                }
            }
        }
    }
}
