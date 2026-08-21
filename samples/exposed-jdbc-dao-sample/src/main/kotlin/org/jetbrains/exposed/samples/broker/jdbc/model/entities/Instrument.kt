@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.jdbc.model.entities

import org.jetbrains.exposed.samples.broker.jdbc.model.tables.InstrumentTags
import org.jetbrains.exposed.samples.broker.jdbc.model.tables.Instruments
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

class Instrument(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Instrument>(Instruments)

    var ticker by Instruments.ticker
    var name by Instruments.name
    var type by Instruments.type
    var tags by Tag via InstrumentTags
}
