@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class PortfolioDTO(val id: Int? = null, val name: String, val clientId: Int)

@Serializable
data class PortfolioSummaryDTO(val id: Int, val name: String, val createdAt: String)

@Serializable
data class PortfolioDetailDTO(val id: Int, val name: String, val createdAt: String, val trades: List<TradeDetailDTO>)
