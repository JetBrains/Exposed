@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.timestamp

object Portfolios : IntIdTable("portfolios") {
    val name = varchar("name", 128)
    val client = reference("client_id", Clients)
    val createdAt = timestamp("created_at")
}
