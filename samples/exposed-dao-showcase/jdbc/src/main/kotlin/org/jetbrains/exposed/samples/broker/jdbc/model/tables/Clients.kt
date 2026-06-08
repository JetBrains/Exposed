@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.jdbc.model.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object Clients : IntIdTable("clients") {
    val name = varchar("name", 128)
    val email = varchar("email", 256).uniqueIndex()
    val broker = reference("broker_id", Brokers)
}
