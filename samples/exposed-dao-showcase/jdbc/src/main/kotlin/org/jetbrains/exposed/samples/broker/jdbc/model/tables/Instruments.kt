@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.jdbc.model.tables

import org.jetbrains.exposed.samples.broker.jdbc.model.InstrumentType
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object Instruments : IntIdTable("instruments") {
    val ticker = varchar("ticker", 16).uniqueIndex()
    val name = varchar("name", 256)
    val type = enumerationByName<InstrumentType>("type", 16)
}
