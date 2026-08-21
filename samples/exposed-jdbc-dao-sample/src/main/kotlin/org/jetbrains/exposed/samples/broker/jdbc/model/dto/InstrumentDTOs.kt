@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.jdbc.model.dto

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.samples.broker.jdbc.model.InstrumentType

@Serializable
data class InstrumentDTO(val id: Int? = null, val ticker: String, val name: String, val type: InstrumentType)

@Serializable
data class InstrumentDetailDTO(val id: Int, val ticker: String, val name: String, val type: InstrumentType, val tags: List<String>)

@Serializable
data class TagAssignmentDTO(val tags: List<String>)
