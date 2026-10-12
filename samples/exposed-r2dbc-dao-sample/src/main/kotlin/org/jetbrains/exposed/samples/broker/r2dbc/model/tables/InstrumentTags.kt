@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.tables

import org.jetbrains.exposed.v1.core.Table

object InstrumentTags : Table("instrument_tags") {
    val instrument = reference("instrument_id", Instruments)
    val tag = reference("tag_id", Tags)
    override val primaryKey = PrimaryKey(instrument, tag)
}
