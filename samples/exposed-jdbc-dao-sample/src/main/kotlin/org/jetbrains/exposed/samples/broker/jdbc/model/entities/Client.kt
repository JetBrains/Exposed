@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.jdbc.model.entities

import org.jetbrains.exposed.samples.broker.jdbc.model.tables.Clients
import org.jetbrains.exposed.samples.broker.jdbc.model.tables.Portfolios
import org.jetbrains.exposed.samples.broker.jdbc.model.tables.Trades
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

class Client(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Client>(Clients)

    var name by Clients.name
    var email by Clients.email
    var broker by Broker referencedOn Clients.broker
    val portfolios by Portfolio referrersOn Portfolios.client
    val trades by Trade referrersOn Trades.client
}
