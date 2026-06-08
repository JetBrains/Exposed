@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.entities

import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.r2dbc.dao.IntEntityClass
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.InstrumentTags
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Instruments
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class Instrument(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Instrument>(Instruments)

    var ticker by Instruments.ticker
    var name by Instruments.name
    var type by Instruments.type
    var tags by Tag via InstrumentTags
}
