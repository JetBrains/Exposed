package org.jetbrains.exposed.v1.core.dao.id.java

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.java.javaUUID
import java.util.UUID

/**
 * Identity table with a primary key consisting of an auto-generating [java.util.UUID] value.
 *
 * **Note** The specific UUID column type used depends on the database.
 * The stored identity value will be auto-generated on the client side just before insertion of a new row.
 *
 * @param name Table name. By default, this will be resolved from any class name with a "Table" suffix removed (if present).
 * @param columnName Name for the primary key column. By default, "id" is used.
 */
open class UUIDTable(name: String = "", columnName: String = "id") : IdTable<UUID>(name) {
    /** The identity column of this [UUIDTable], for storing java.util.UUID values wrapped as [EntityID] instances. */
    final override val id: Column<EntityID<UUID>> = javaUUID(columnName).autoGenerate().entityId()
    final override val primaryKey = PrimaryKey(id)
}
