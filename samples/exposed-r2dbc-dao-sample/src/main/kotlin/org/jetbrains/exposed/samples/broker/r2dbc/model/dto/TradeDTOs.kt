@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.dto

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.samples.broker.r2dbc.model.TradeType

@Serializable
data class TradeRequestDTO(
    val clientId: Int,
    val instrumentId: Int,
    val portfolioId: Int? = null,
    val type: TradeType,
    val quantity: Int,
    val price: String
)

@Serializable
data class TradeDetailDTO(
    val id: Int,
    val instrumentTicker: String,
    val instrumentName: String,
    val type: TradeType,
    val quantity: Int,
    val price: String,
    val executedAt: String,
    val portfolioName: String? = null
)
