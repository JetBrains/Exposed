package org.jetbrains.exposed.r2dbc.sql

import org.jetbrains.exposed.r2dbc.sql.vendors.currentDialectMetadata
import org.jetbrains.exposed.sql.Schema
import org.jetbrains.exposed.sql.Sequence
import org.jetbrains.exposed.sql.Table

/**
 * Checks if this schema exists or not.
 */
suspend fun Schema.exists(): Boolean = currentDialectMetadata.schemaExists(this)

/**
 * Returns whether [this] table exists in the database.
 *
 * @sample org.jetbrains.exposed.sql.tests.shared.DDLTests.tableExists02
 */
suspend fun Table.exists(): Boolean = currentDialectMetadata.tableExists(this)

/**
 * Returns whether this sequence exists in the database.
 */
suspend fun Sequence.exists(): Boolean = currentDialectMetadata.sequenceExists(this)
