@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.entities

import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Brokers
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Clients
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass

class Broker(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Broker>(Brokers)

    var name by Brokers.name
    var licenseNumber by Brokers.licenseNumber

    val clients by Client referrersOn Clients.broker
}
