@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.jdbc.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.samples.broker.jdbc.model.dto.*
import org.jetbrains.exposed.samples.broker.jdbc.model.entities.*
import org.jetbrains.exposed.samples.broker.jdbc.model.tables.Tags
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.load
import org.jetbrains.exposed.v1.jdbc.SizedCollection
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.instrumentRoutes() {
    routing {
        route("/instruments") {
            post {
                val dto = call.receive<InstrumentDTO>()
                val result = transaction {
                    val instrument = Instrument.new {
                        ticker = dto.ticker
                        name = dto.name
                        type = dto.type
                    }
                    InstrumentDTO(instrument.id.value, instrument.ticker, instrument.name, instrument.type)
                }
                call.respond(HttpStatusCode.Created, result)
            }

            get {
                val instruments = transaction {
                    Instrument.all().map { inst ->
                        InstrumentDetailDTO(
                            id = inst.id.value,
                            ticker = inst.ticker,
                            name = inst.name,
                            type = inst.type,
                            tags = inst.tags.map { it.name }
                        )
                    }
                }
                call.respond(instruments)
            }

            get("{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                val detail = transaction {
                    val inst = Instrument.findById(id) ?: return@transaction null
                    inst.load(Instrument::tags)
                    InstrumentDetailDTO(
                        id = inst.id.value,
                        ticker = inst.ticker,
                        name = inst.name,
                        type = inst.type,
                        tags = inst.tags.map { it.name }
                    )
                }
                if (detail != null) call.respond(detail)
                else call.respond(HttpStatusCode.NotFound, "Instrument not found")
            }

            put("{id}/tags") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest, "Invalid ID")
                val dto = call.receive<TagAssignmentDTO>()
                val result = transaction {
                    val instrument = Instrument.findById(id)
                        ?: return@transaction null
                    val tags = dto.tags.map { tagName ->
                        Tag.find { Tags.name eq tagName }.firstOrNull()
                            ?: Tag.new { name = tagName }
                    }
                    instrument.tags = SizedCollection(tags)
                    InstrumentDetailDTO(
                        id = instrument.id.value,
                        ticker = instrument.ticker,
                        name = instrument.name,
                        type = instrument.type,
                        tags = instrument.tags.map { it.name }
                    )
                }
                if (result != null) call.respond(result)
                else call.respond(HttpStatusCode.NotFound, "Instrument not found")
            }
        }
    }
}
