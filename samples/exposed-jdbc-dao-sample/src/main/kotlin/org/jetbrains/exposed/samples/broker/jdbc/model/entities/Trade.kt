@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.jdbc.model.entities

import org.jetbrains.exposed.samples.broker.jdbc.model.tables.Trades
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

class Trade(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Trade>(Trades)

    var client by Client referencedOn Trades.client
    var instrument by Instrument referencedOn Trades.instrument
    var portfolio by Portfolio optionalReferencedOn Trades.portfolio
    var type by Trades.type
    var quantity by Trades.quantity
    var price by Trades.price
    var executedAt by Trades.executedAt
}
