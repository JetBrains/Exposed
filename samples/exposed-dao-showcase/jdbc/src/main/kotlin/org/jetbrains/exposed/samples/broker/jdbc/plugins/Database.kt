@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.jdbc.plugins

import io.ktor.server.application.*
import org.jetbrains.exposed.samples.broker.jdbc.model.tables.*
import org.jetbrains.exposed.v1.dao.EntityHook
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureDatabase() {
    Database.connect("jdbc:h2:mem:broker;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
    transaction {
        SchemaUtils.create(Brokers, Clients, Portfolios, Instruments, Tags, InstrumentTags, Trades)
    }

    EntityHook.subscribe { change ->
        val table = change.entityClass.table.tableName
        val action = change.changeType.name.lowercase()
        log.info("Entity hook: $action on $table (id=${change.entityId})")
    }
}
