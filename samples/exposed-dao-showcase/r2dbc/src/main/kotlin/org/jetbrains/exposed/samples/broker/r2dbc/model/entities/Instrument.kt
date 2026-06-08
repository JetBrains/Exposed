@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.entities

import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.InstrumentTags
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Instruments
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class Instrument(id: EntityID<Int>) : IntR2dbcEntity(id) {
    companion object : IntR2dbcEntityClass<Instrument>(Instruments)

    var ticker by Instruments.ticker
    var name by Instruments.name
    var type by Instruments.type
    val tags by Tag viaSuspend InstrumentTags
}
