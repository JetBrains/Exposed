@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.broker.r2dbc.model.tables

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

object Tags : IntIdTable("tags") {
    val name = varchar("name", 64).uniqueIndex()
}
