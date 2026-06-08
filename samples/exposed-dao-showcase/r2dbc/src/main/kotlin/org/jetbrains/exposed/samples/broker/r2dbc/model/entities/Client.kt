@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.entities

import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.relationships.referencedOnSuspend
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Clients
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Portfolios
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Trades
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class Client(id: EntityID<Int>) : IntR2dbcEntity(id) {
    companion object : IntR2dbcEntityClass<Client>(Clients)

    var name by Clients.name
    var email by Clients.email
    val broker by Broker referencedOnSuspend Clients.broker
    val portfolios by Portfolio referrersOnSuspend Portfolios.client
    val trades by Trade referrersOnSuspend Trades.client
}
