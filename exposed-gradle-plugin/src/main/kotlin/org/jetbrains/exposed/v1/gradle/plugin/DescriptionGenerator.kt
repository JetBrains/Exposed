package org.jetbrains.exposed.v1.gradle.plugin

private const val HASH_LENGTH: Int = 8

/**
 * Extracts the operation type and table name from the SQL statement
 * and formats them into a standardized filename sub-pattern.
 *
 * @return A formatted part of a filename that describes the SQL statement
 */
internal fun String.statementToFileDescription(useUpperCase: Boolean): String {
    val normalizedStatement = trim().replace(Regex("\\s+"), " ")

    return when {
        // CREATE TABLE statements
        normalizedStatement.startsWith("CREATE TABLE", ignoreCase = true) -> {
            val tableName = normalizedStatement.extractTableName("CREATE TABLE")
            "CREATE_TABLE_$tableName".inRequestedCase(useUpperCase)
        }

        // ALTER TABLE statements
        normalizedStatement.startsWith("ALTER TABLE", ignoreCase = true) -> {
            val tableName = normalizedStatement.extractTableName("ALTER TABLE")
            // Check if it's a constraint addition or drop
            if (normalizedStatement.contains("ADD CONSTRAINT", ignoreCase = true)) {
                val constraintName = normalizedStatement.extractConstraintName("ADD CONSTRAINT")
                "ALTER_TABLE_${tableName}_ADD_CONSTRAINT_$constraintName".inRequestedCase(useUpperCase)
            } else if (normalizedStatement.contains("DROP CONSTRAINT", ignoreCase = true)) {
                val constraintName = normalizedStatement.extractConstraintName("DROP CONSTRAINT")
                "ALTER_TABLE_${tableName}_DROP_CONSTRAINT_$constraintName".inRequestedCase(useUpperCase)
            } else {
                "ALTER_TABLE_$tableName".inRequestedCase(useUpperCase)
            }
        }

        // CREATE SEQUENCE statements
        normalizedStatement.startsWith("CREATE SEQUENCE", ignoreCase = true) -> {
            // Check if it's a new sequence for a new table
            if (normalizedStatement.contains("CREATE TABLE", ignoreCase = true)) {
                val tableName = normalizedStatement.extractTableName("CREATE TABLE")
                "CREATE_TABLE_$tableName".inRequestedCase(useUpperCase)
            } else {
                val sequenceName = normalizedStatement.extractSequenceName("CREATE SEQUENCE")
                "CREATE_SEQUENCE_$sequenceName".inRequestedCase(useUpperCase)
            }
        }

        // CREATE INDEX or CREATE ??? INDEX statements
        Regex("^CREATE\\s+(\\w+\\s+)*INDEX\\s+").containsMatchIn(normalizedStatement.uppercase()) -> {
            val indexInfo = normalizedStatement.extractIndexInfo(" INDEX ")
            "CREATE_INDEX_$indexInfo".inRequestedCase(useUpperCase)
        }

        // DROP TABLE statements
        normalizedStatement.startsWith("DROP TABLE", ignoreCase = true) -> {
            val tableName = normalizedStatement.extractTableName("DROP TABLE")
            "DROP_TABLE_$tableName".inRequestedCase(useUpperCase)
        }

        // DROP INDEX statements
        normalizedStatement.startsWith("DROP INDEX", ignoreCase = true) -> {
            val indexInfo = normalizedStatement.extractIndexInfo("DROP INDEX")
            "DROP_INDEX_$indexInfo".inRequestedCase(useUpperCase)
        }

        else -> {
            val operation = normalizedStatement.split(" ").first()
            val tableName = normalizedStatement.extractAnyTableNameOrNull()

            if (tableName != null) {
                "${operation}_$tableName".inRequestedCase(useUpperCase)
            } else {
                val hash = normalizedStatement.hashCode().toString().replace("-", "").take(HASH_LENGTH)
                "${operation}_STATEMENT_$hash".inRequestedCase(useUpperCase)
            }
        }
    }
}

private fun String.inRequestedCase(useUpperCase: Boolean): String = if (useUpperCase) uppercase() else lowercase()

private fun String.extractTableName(prefix: String): String {
    val afterPrefix = substringAfterPrefix(prefix)

    // Handle potential "IF NOT EXISTS" or "IF EXISTS"
    val tableNamePart = afterPrefix.substringAfterExists()

    // Extract the table name (everything up to the first space, parenthesis, or end of string)
    val tableName = tableNamePart.split(Regex("[ (]"))[0].trim()

    return tableName.sanitized()
}

private fun String.extractSequenceName(prefix: String): String {
    val afterPrefix = substringAfterPrefix(prefix)

    // Handle potential "IF NOT EXISTS" or "IF EXISTS"
    val sequenceNamePart = afterPrefix.substringAfterExists()

    // Extract the sequence name (everything up to the first space or end of string)
    val sequenceName = sequenceNamePart.split(" ")[0].trim()

    return sequenceName.sanitized()
}

private fun String.extractConstraintName(prefix: String): String {
    val constraintPart = substringAfterPrefix(prefix)

    // Extract the constraint name (everything up to the first space or end of string)
    val constraintName = constraintPart.split(" ")[0].trim()

    return constraintName.sanitized()
}

private fun String.extractIndexInfo(prefix: String): String {
    val afterPrefix = substringAfterPrefix(prefix)
    val indexName = afterPrefix.split(" ")[0].trim()

    // Extract the table name (after "ON")
    val onKey = " ON "
    val onIndex = indexOf(" ON ", ignoreCase = true)
    if (onIndex != -1) {
        val afterOn = substring(onIndex + onKey.length).trim()
        val tableName = afterOn.split(Regex("[ (]"))[0].trim()

        return "${indexName.sanitized()}_ON_${tableName.sanitized()}"
    }

    return indexName.sanitized()
}

private fun String.extractAnyTableNameOrNull(): String? {
    val sqlDelimiter = Regex("[ ,;(]")

    // Special case for UPDATE statements
    val updateKey = "UPDATE"
    if (startsWith(updateKey, ignoreCase = true)) {
        val afterUpdate = substring(updateKey.length).trim()
        val tableName = afterUpdate.split(sqlDelimiter)[0].trim()
        if (tableName.isNotEmpty()) {
            return tableName.sanitized()
        }
    }

    // Common SQL keywords that are followed by a table name
    val tableKeywords = listOf("TABLE", "FROM", "INTO", "JOIN")

    for (keyword in tableKeywords) {
        // Look for the keyword with word boundaries
        val regex = Regex("\\b$keyword\\b", RegexOption.IGNORE_CASE)
        val match = regex.find(this)
        if (match != null) {
            val afterKeyword = substring(match.range.last + 1).trim()
            val potentialTableName = afterKeyword.split(sqlDelimiter)[0].trim()
            if (potentialTableName.isNotEmpty()) {
                return potentialTableName.sanitized()
            }
        }
    }

    return null
}

private fun String.substringAfterPrefix(prefix: String): String {
    return substring(indexOf(prefix, ignoreCase = true) + prefix.length).trim()
}

private fun String.substringAfterExists(): String {
    val ifNotExistsClause = "IF NOT EXISTS"
    val ifExistsClause = "IF EXISTS"
    return when {
        startsWith(ifNotExistsClause, ignoreCase = true) -> substring(ifNotExistsClause.length).trim()

        startsWith(ifExistsClause, ignoreCase = true) -> substring(ifExistsClause.length).trim()

        else -> this
    }
}

private fun String.sanitized(): String {
    var result = replace("\"", "")
        .replace("`", "")
        .replace("'", "")

    // Remove schema prefix if present (e.g., "schema.table" -> "table"; "schema.public.table" -> "table")
    if (result.contains(".")) {
        result = result.substring(result.lastIndexOf(".") + 1)
    }

    return result
}
