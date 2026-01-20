package org.jetbrains.exposed.v1.core.java

import org.jetbrains.exposed.v1.core.BasicUuidColumnType
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Binary column for storing [java.util.UUID].
 */
class UUIDColumnType : BasicUuidColumnType<UUID>() {
    override fun valueFromDB(value: Any): UUID = when (value) {
        is UUID -> value
        is ByteArray -> ByteBuffer.wrap(value).let { b -> UUID(b.long, b.long) }
        is String if value.isHexAndDashFormat() -> UUID.fromString(value)
        is String -> ByteBuffer.wrap(value.toByteArray()).let { b -> UUID(b.long, b.long) }
        is ByteBuffer -> value.let { b -> UUID(b.long, b.long) }
        else -> error("Unexpected value of type UUID: $value of ${value::class.qualifiedName}")
    }

    override fun notNullValueToDB(value: UUID): Any {
        return originalDataTypeProvider.uuidToDB(value)
    }
}

/** Creates a binary column, with the specified [name], for storing [java.util.UUID] values. */
fun Table.javaUUID(name: String): Column<UUID> = registerColumn(name, UUIDColumnType())
