@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.entities

import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.relationships.optionalReferencedOnSuspend
import org.jetbrains.exposed.r2dbc.dao.relationships.referencedOnSuspend
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Trades
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class Trade(id: EntityID<Int>) : IntR2dbcEntity(id) {
    companion object : IntR2dbcEntityClass<Trade>(Trades)

    val client by Client referencedOnSuspend Trades.client
    val instrument by Instrument referencedOnSuspend Trades.instrument
    val portfolio by Portfolio optionalReferencedOnSuspend Trades.portfolio
    var type by Trades.type
    var quantity by Trades.quantity
    var price by Trades.price
    var executedAt by Trades.executedAt
}
