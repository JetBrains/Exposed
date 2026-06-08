@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.jdbc.model.entities

import org.jetbrains.exposed.samples.broker.jdbc.model.tables.Brokers
import org.jetbrains.exposed.samples.broker.jdbc.model.tables.Clients
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

class Broker(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Broker>(Brokers)

    var name by Brokers.name
    var licenseNumber by Brokers.licenseNumber
    val clients by Client referrersOn Clients.broker
}
