@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class BrokerDTO(val id: Int? = null, val name: String, val licenseNumber: String)

@Serializable
data class BrokerSummaryDTO(val id: Int, val name: String, val licenseNumber: String, val clientCount: Long)

@Serializable
data class BrokerDetailDTO(val id: Int, val name: String, val licenseNumber: String, val clients: List<ClientSummaryDTO>)
