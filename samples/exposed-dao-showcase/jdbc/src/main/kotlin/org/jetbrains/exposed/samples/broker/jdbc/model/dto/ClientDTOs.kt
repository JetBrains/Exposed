@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.jdbc.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class ClientDTO(val id: Int? = null, val name: String, val email: String, val brokerId: Int)

@Serializable
data class ClientSummaryDTO(val id: Int, val name: String, val email: String)

@Serializable
data class ClientDetailDTO(
    val id: Int,
    val name: String,
    val email: String,
    val broker: BrokerDTO,
    val portfolios: List<PortfolioSummaryDTO>
)
