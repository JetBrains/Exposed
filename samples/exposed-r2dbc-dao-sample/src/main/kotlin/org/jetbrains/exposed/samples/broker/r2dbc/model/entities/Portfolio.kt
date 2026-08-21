@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.entities

import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Portfolios
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Trades
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntity
import org.jetbrains.exposed.v1.dao.r2dbc.IntEntityClass

class Portfolio(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Portfolio>(Portfolios)

    var name by Portfolios.name
    val client by Client referencedOn Portfolios.client
    var createdAt by Portfolios.createdAt
    val trades by Trade optionalReferrersOn Trades.portfolio
}
