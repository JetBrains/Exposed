@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.flushCache
import org.jetbrains.exposed.r2dbc.dao.relationships.load
import org.jetbrains.exposed.samples.broker.r2dbc.model.dto.*
import org.jetbrains.exposed.samples.broker.r2dbc.model.entities.*
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Tags
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.SizedCollection
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

fun Application.instrumentRoutes() {
    routing {
        route("/instruments") {
            post {
                val dto = call.receive<InstrumentDTO>()
                val result = suspendTransaction {
                    val instrument = Instrument.new {
                        ticker = dto.ticker
                        name = dto.name
                        type = dto.type
                    }.flush()
                    InstrumentDTO(instrument.id.value, instrument.ticker, instrument.name, instrument.type)
                }
                call.respond(HttpStatusCode.Created, result)
            }

            get {
                val instruments = suspendTransaction {
                    Instrument.all().toList().map { inst ->
                        InstrumentDetailDTO(
                            id = inst.id.value,
                            ticker = inst.ticker,
                            name = inst.name,
                            type = inst.type,
                            tags = inst.tags.toList().map { it.name }
                        )
                    }
                }
                call.respond(instruments)
            }

            get("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                val detail = suspendTransaction {
                    val inst = Instrument.findById(id) ?: return@suspendTransaction null
                    inst.load(Instrument::tags)
                    InstrumentDetailDTO(
                        id = inst.id.value,
                        ticker = inst.ticker,
                        name = inst.name,
                        type = inst.type,
                        tags = inst.tags.map { it.name }.toList()
                    )
                }
                if (detail != null) {
                    call.respond(detail)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Instrument not found")
                }
            }

            put("{id}/tags") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                val dto = call.receive<TagAssignmentDTO>()
                val result = suspendTransaction {
                    val instrument = Instrument.findById(id)
                        ?: return@suspendTransaction null
                    val tags = dto.tags.map { tagName ->
                        Tag.find { Tags.name eq tagName }.firstOrNull()
                            ?: Tag.new { name = tagName }.flush()
                    }
                    instrument.tags = SizedCollection(tags)
                    flushCache()
                    InstrumentDetailDTO(
                        id = instrument.id.value,
                        ticker = instrument.ticker,
                        name = instrument.name,
                        type = instrument.type,
                        tags = instrument.tags.map { it.name }.toList()
                    )
                }
                if (result != null) {
                    call.respond(result)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Instrument not found")
                }
            }
        }
    }
}
