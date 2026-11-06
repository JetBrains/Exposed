package org.jetbrains.exposed.v1.plugin.core.tables

import java.io.Serializable

/**
 * The exact Exposed code to generate for a single database column, which should be used instead of any definition that
 * the generator would otherwise produce. This can be used for a column whose type is not supported, or whose generated
 * type is not desired.
 *
 * The generator will use [code] unchanged as the column's factory call and still derives the modifiers
 * (`.nullable()`, `.autoIncrement()`, `.index()`, `.databaseGenerated()`) from the database, so [code] must not
 * contain them. The code is not compiled by the generator, so a mistake only shows up when the generated file is compiled.
 */
data class ColumnMapping(
    /**
     * The column to map, as `TABLE.COLUMN`, or `SCHEMA.TABLE.COLUMN` for a table outside the connection's current
     * schema. Each part is matched as reported and in the database's default identifier case, so `events.created_at`
     * matches `EVENTS.CREATED_AT` on H2. A part may be quoted to match a name that is not in that case.
     */
    val column: String,

    /** The column factory call, for example `timestamp("CREATED_AT")`, including the column name. */
    val code: String,

    /**
     * Fully qualified names that [code] needs imported, for example `com.example.Payload`. When empty, the import of
     * an Exposed extension function that the generator itself knows (`timestamp`, `json`, `javaUUID`, ...) is inferred.
     */
    val imports: List<String> = emptyList(),
) : Serializable {
    init {
        require(column.isNotBlank()) { "A column mapping needs a column key of the form TABLE.COLUMN or SCHEMA.TABLE.COLUMN" }
        require(code.isNotBlank()) { "The column mapping for '$column' needs the code to generate" }
    }

    /**
     * The parts of [column]: an optional schema, the table, and the column, each still potentially quoted.
     * @throws IllegalArgumentException if the key does not have two or three parts.
     */
    val keyParts: List<String>
        get() {
            val parts = column.split('.').map { it.trim() }
            require(parts.size in UNQUALIFIED_KEY_PARTS..QUALIFIED_KEY_PARTS && parts.all { it.isNotEmpty() }) {
                "The column mapping key '$column' must be of the form TABLE.COLUMN or SCHEMA.TABLE.COLUMN"
            }
            return parts
        }

    /** The schema part of the key, or `null` when the table is expected in the connection's current schema. */
    val schemaPart: String?
        get() = keyParts.takeIf { it.size == QUALIFIED_KEY_PARTS }?.first()

    /** The table part of the key. */
    val tablePart: String
        get() = keyParts[keyParts.size - UNQUALIFIED_KEY_PARTS]

    /** The column part of the key. */
    val columnPart: String
        get() = keyParts.last()

    private companion object {
        // KEEP - for Detekt (or try suppressing)
        private const val serialVersionUID: Long = 1L

        const val UNQUALIFIED_KEY_PARTS: Int = 2

        const val QUALIFIED_KEY_PARTS: Int = 3
    }
}
