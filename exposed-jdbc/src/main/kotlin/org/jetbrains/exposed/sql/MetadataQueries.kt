package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.vendors.currentDialectMetadata

/**
 * Checks if this schema exists or not.
 */
fun Schema.exists(): Boolean = currentDialectMetadata.schemaExists(this)

/**
 * Returns whether [this] table exists in the database.
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.DDLTests.tableExists02
 */
fun Table.exists(): Boolean = currentDialectMetadata.tableExists(this)

/**
 * Returns whether this sequence exists in the database.
 */
fun Sequence.exists(): Boolean = currentDialectMetadata.sequenceExists(this)
