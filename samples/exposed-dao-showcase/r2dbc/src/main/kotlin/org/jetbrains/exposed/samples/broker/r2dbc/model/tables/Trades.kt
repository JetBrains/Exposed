@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.tables

import org.jetbrains.exposed.samples.broker.r2dbc.model.TradeType
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.timestamp

object Trades : IntIdTable("trades") {
    val client = reference("client_id", Clients)
    val instrument = reference("instrument_id", Instruments)
    val portfolio = optReference("portfolio_id", Portfolios)
    val type = enumerationByName<TradeType>("type", 8)
    val quantity = integer("quantity")
    val price = decimal("price", 12, 4)
    val executedAt = timestamp("executed_at")
}
