@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.jdbc.model.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object Brokers : IntIdTable("brokers") {
    val name = varchar("name", 128)
    val licenseNumber = varchar("license_number", 32).uniqueIndex()
}
