package org.jetbrains.exposed.serialization

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object DAOSerializer {

    /**
     * A disgusting, performance-heavy way to create a JsonPrimitive from a generic primitive.
     * @param value A primitive type such as a Number, String, or null.
     */
    fun toElement(value: Any?): JsonPrimitive { // Horribly inefficient way of doing this (probably)
        return if (value != null) {
            val str = value.toString()
            try {
                JsonPrimitive(str.toInt())
            } catch (notInt: NumberFormatException) {
                try {
                    JsonPrimitive(str.toFloat())
                } catch (notFloat: NumberFormatException) {
                    try {
                        JsonPrimitive(str.toBooleanStrict())
                    } catch (isString: IllegalArgumentException) {
                        JsonPrimitive(str)
                    }
                }
            }
        } else JsonNull
    }

    /**
     * Serialize a ResultRow.
     * @param table The Table the row belongs to. Leave null to disable further serialization of referenced rows.
     */
    fun jsonify(row: ResultRow, table: Table? = null): JsonObject {
        return buildJsonObject {
            row.fieldIndex.keys.forEach { field ->
                val value = row[field]
                val name = field.toString().substringAfterLast('.')
                if (table != null) {
                    val refColumns = table.columns.filter { (it.name == name).and(it.referee != null) }
                    if (refColumns.isNotEmpty()) {
                        for (refCol in refColumns) {
                            val referee = refCol.referee!!
                            val referencedValue = referee.table.select {
                                referee eq referee.asLiteral(value)
                            }.first()
                            put(name, jsonify(referencedValue))
                        }
                    } else put(name, toElement(value))
                } else put(name, toElement(value)) // Should clean this up
            }
        }
    }

    /**
     * Serialize a DAO entity, including its referenced rows.
     */
    fun jsonify(dao: Entity<Int>, recursion: Boolean = false): JsonObject {
        val columns = dao.klass.dependsOnColumns
        val values = dao.readValues
        return buildJsonObject {
            for (column in columns) {
                val referee = column.referee
                if (referee != null) {
                    transaction {
                        val referencedRow = referee.table.select {
                            referee eq referee.asLiteral(values[column])
                        }.single()
                        if (recursion) put(column.name, jsonify(referencedRow, referee.table))
                        else put(column.name, jsonify(referencedRow))
                    }
                } else {
                    put(column.name, toElement(values[column]))
                }
            }
        }
    }
}
