package org.jetbrains.exposed.migration.plugin

private const val HASH_LENGTH: Int = 8

/**
 * The function extracts the operation type and table name from the SQL statement
 * and formats them into a standardized filename pattern.
 *
 * @return A formatted filename for the SQL statement
 */
fun String.statementToFileName(): String {
    val normalizedStatement = trim().replace(Regex("\\s+"), " ")

    return when {
        // CREATE TABLE statements
        normalizedStatement.startsWith("CREATE TABLE", ignoreCase = true) -> {
            val tableName = extractTableName("CREATE TABLE", normalizedStatement)
            "CREATE_TABLE_$tableName"
        }

        // ALTER TABLE statements
        normalizedStatement.startsWith("ALTER TABLE", ignoreCase = true) -> {
            val tableName = extractTableName("ALTER TABLE", normalizedStatement)

            // Check if it's a constraint addition
            if (normalizedStatement.contains("ADD CONSTRAINT", ignoreCase = true)) {
                val constraintName = extractConstraintName(normalizedStatement)
                "ALTER_TABLE_${tableName}_ADD_CONSTRAINT_$constraintName"
            } else {
                "ALTER_TABLE_$tableName"
            }
        }

        // CREATE SEQUENCE statements
        normalizedStatement.startsWith("CREATE SEQUENCE", ignoreCase = true) -> {
            val sequenceName = extractSequenceName(normalizedStatement)
            "CREATE_SEQUENCE_$sequenceName"
        }

        // DROP TABLE statements
        normalizedStatement.startsWith("DROP TABLE", ignoreCase = true) -> {
            val tableName = extractTableName("DROP TABLE", normalizedStatement)
            "DROP_TABLE_$tableName"
        }

        // CREATE INDEX statements
        normalizedStatement.startsWith("CREATE INDEX", ignoreCase = true) -> {
            val indexInfo = extractIndexInfo(normalizedStatement)
            "CREATE_INDEX_$indexInfo"
        }

        else -> {
            val operation = normalizedStatement.split(" ").first().uppercase()
            val tableName = tryExtractAnyTableName(normalizedStatement)

            if (tableName.isNotEmpty()) {
                "${operation}_$tableName"
            } else {
                val hash = normalizedStatement.hashCode().toString().replace("-", "").take(HASH_LENGTH)
                "${operation}_STATEMENT_$hash"
            }
        }
    }
}

private fun extractTableName(prefix: String, statement: String): String {
    // Remove the prefix and get the table name part
    val afterPrefix = statement.substring(statement.indexOf(prefix, ignoreCase = true) + prefix.length).trim()

    // Handle "IF NOT EXISTS" in CREATE TABLE statements or "IF EXISTS" in DROP TABLE statements
    val tableNamePart = when {
        afterPrefix.startsWith("IF NOT EXISTS", ignoreCase = true) -> {
            afterPrefix.substring("IF NOT EXISTS".length).trim()
        }

        afterPrefix.startsWith("IF EXISTS", ignoreCase = true) -> {
            afterPrefix.substring("IF EXISTS".length).trim()
        }

        else -> {
            afterPrefix
        }
    }

    // Extract the table name (everything up to the first space, parenthesis, or end of string)
    val tableName = tableNamePart.split(Regex("[ (]"))[0].trim()

    // Remove quotes and schema prefixes if present
    return sanitizeIdentifier(tableName)
}

private fun extractSequenceName(statement: String): String {
    // Remove the prefix and get the sequence name part
    val afterPrefix =
        statement.substring(statement.indexOf("CREATE SEQUENCE", ignoreCase = true) + "CREATE SEQUENCE".length).trim()

    // Handle "IF NOT EXISTS" in CREATE SEQUENCE statements
    val sequenceNamePart = if (afterPrefix.startsWith("IF NOT EXISTS", ignoreCase = true)) {
        afterPrefix.substring("IF NOT EXISTS".length).trim()
    } else {
        afterPrefix
    }

    // Extract the sequence name (everything up to the first space or end of string)
    val sequenceName = sequenceNamePart.split(" ")[0].trim()

    // Remove quotes and schema prefixes if present
    return sanitizeIdentifier(sequenceName)
}

private fun extractConstraintName(statement: String): String {
    val constraintPart =
        statement.substring(statement.indexOf("ADD CONSTRAINT", ignoreCase = true) + "ADD CONSTRAINT".length).trim()

    // Extract the constraint name (everything up to the first space or end of string)
    val constraintName = constraintPart.split(" ")[0].trim()

    // Remove quotes if present
    return sanitizeIdentifier(constraintName)
}

private fun extractIndexInfo(statement: String): String {
    // Extract the index name
    val afterPrefix =
        statement.substring(statement.indexOf("CREATE INDEX", ignoreCase = true) + "CREATE INDEX".length).trim()
    val indexName = afterPrefix.split(" ")[0].trim()

    // Extract the table name (after "ON")
    val onIndex = statement.indexOf(" ON ", ignoreCase = true)
    if (onIndex != -1) {
        val afterOn = statement.substring(onIndex + " ON ".length).trim()
        val tableName = afterOn.split(Regex("[ (]"))[0].trim()

        return "${sanitizeIdentifier(indexName)}_ON_${sanitizeIdentifier(tableName)}"
    }

    return sanitizeIdentifier(indexName)
}

private fun tryExtractAnyTableName(statement: String): String {
    // Special case for UPDATE statements
    if (statement.startsWith("UPDATE", ignoreCase = true)) {
        val afterUpdate = statement.substring("UPDATE".length).trim()
        val tableName = afterUpdate.split(Regex("[ ,;(]"))[0].trim()
        if (tableName.isNotEmpty()) {
            return sanitizeIdentifier(tableName)
        }
    }

    // Common SQL keywords that are followed by a table name
    val tableKeywords = listOf("TABLE", "FROM", "INTO", "JOIN")

    for (keyword in tableKeywords) {
        // Look for the keyword with word boundaries
        val regex = Regex("\\b$keyword\\b", RegexOption.IGNORE_CASE)
        val match = regex.find(statement)
        if (match != null) {
            val afterKeyword = statement.substring(match.range.last + 1).trim()
            val potentialTableName = afterKeyword.split(Regex("[ ,;(]"))[0].trim()
            if (potentialTableName.isNotEmpty()) {
                return sanitizeIdentifier(potentialTableName)
            }
        }
    }

    return ""
}

private fun sanitizeIdentifier(identifier: String): String {
    var result = identifier
        .replace("\"", "")
        .replace("`", "")
        .replace("'", "")

    // Remove schema prefix if present (e.g., "schema.table" -> "table")
    if (result.contains(".")) {
        result = result.substring(result.lastIndexOf(".") + 1)
    }

    // Convert to uppercase
    return result.uppercase()
}
