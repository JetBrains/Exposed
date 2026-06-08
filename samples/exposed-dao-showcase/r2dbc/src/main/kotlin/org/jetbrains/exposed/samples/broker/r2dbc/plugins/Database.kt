@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.plugins

import io.ktor.server.application.*
import org.jetbrains.exposed.r2dbc.dao.EntityHook
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.*
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

suspend fun Application.configureDatabase() {
    R2dbcDatabase.connect("r2dbc:h2:mem:///broker;DB_CLOSE_DELAY=-1")
    suspendTransaction {
        SchemaUtils.create(Brokers, Clients, Portfolios, Instruments, Tags, InstrumentTags, Trades)
    }

    EntityHook.subscribe { change ->
        val table = change.entityClass.table.tableName
        val action = change.changeType.name.lowercase()
        log.info("Entity hook: $action on $table (id=${change.entityId})")
    }
}
