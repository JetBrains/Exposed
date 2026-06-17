@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.entities

import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.relationships.referencedOnSuspend
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Portfolios
import org.jetbrains.exposed.samples.broker.r2dbc.model.tables.Trades
import org.jetbrains.exposed.v1.core.dao.id.EntityID

class Portfolio(id: EntityID<Int>) : IntR2dbcEntity(id) {
    companion object : IntR2dbcEntityClass<Portfolio>(Portfolios)

    var name by Portfolios.name
    val client by Client referencedOnSuspend Portfolios.client
    var createdAt by Portfolios.createdAt
    val trades by Trade optionalReferrersOnSuspend Trades.portfolio
}
