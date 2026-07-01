@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.entities

import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.r2dbc.dao.IntEntityClass
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Clients
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Portfolios
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Trades
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class Client(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Client>(Clients)

    var name by Clients.name
    var email by Clients.email
    val broker by Broker referencedOn Clients.broker
    val portfolios by Portfolio referrersOn Portfolios.client
    val trades by Trade referrersOn Trades.client
}
