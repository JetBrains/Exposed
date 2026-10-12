@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.entities

import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Trades
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass

class Trade(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Trade>(Trades)

    val client by Client referencedOn Trades.client
    val instrument by Instrument referencedOn Trades.instrument
    val portfolio by Portfolio optionalReferencedOn Trades.portfolio
    var type by Trades.type
    var quantity by Trades.quantity
    var price by Trades.price
    var executedAt by Trades.executedAt
}
