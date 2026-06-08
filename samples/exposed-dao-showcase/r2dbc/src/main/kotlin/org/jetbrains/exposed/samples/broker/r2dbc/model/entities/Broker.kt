@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.entities

import org.jetbrains.exposed.r2dbc.dao.IntEntity
import org.jetbrains.exposed.r2dbc.dao.IntEntityClass
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Brokers
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Clients
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class Broker(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Broker>(Brokers)

    var name by Brokers.name
    var licenseNumber by Brokers.licenseNumber

    val clients by Client referrersOn Clients.broker
}
