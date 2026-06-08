@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.entities

import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Brokers
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Clients
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class Broker(id: EntityID<Int>) : IntR2dbcEntity(id) {
    companion object : IntR2dbcEntityClass<Broker>(Brokers)

    var name by Brokers.name
    var licenseNumber by Brokers.licenseNumber
    val clients by Client referrersOnSuspend Clients.broker
}
