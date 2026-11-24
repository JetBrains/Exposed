package org.jetbrains.exposed.v1.migration.jdbc

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.vendors.ColumnMetadata
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import kotlin.test.assertTrue

/**
 * Asserts that [actual] SQL equals [expected] SQL, ignoring case and identifier quoting.
 *
 * Whether a dialect folds or quotes a column identifier is covered by `IdentifierQuotingSemanticsTest`;
 * the migration tests that use these helpers verify the statements they generate.
 */
internal fun JdbcTransaction.assertSqlEquals(expected: String, actual: String) {
    assertEquals(unquoted(expected), unquoted(actual))
}

/** Asserts that [actual] SQL starts with [prefix], ignoring case and identifier quoting. See [assertSqlEquals]. */
internal fun JdbcTransaction.assertSqlStartsWith(prefix: String, actual: String) {
    assertTrue(
        unquoted(actual).startsWith(unquoted(prefix)),
        "Expected <$actual> to start with <$prefix>"
    )
}

/** Returns [sql] in lower case and without the quotes that the current dialect puts around identifiers. */
private fun JdbcTransaction.unquoted(sql: String): String {
    val quotes = db.identifierManager.quoteString
    return sql.filterNot { it in quotes }.lowercase()
}

/** Returns the metadata of [column], matched by name regardless of how the current dialect folds or quotes it. */
internal fun List<ColumnMetadata>.metadataFor(column: Column<*>): ColumnMetadata? =
    find { it.name.equals(column.name, ignoreCase = true) }
