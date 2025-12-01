package org.jetbrains.exposed.v1.jdbc

import org.jetbrains.exposed.v1.core.Schema
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.vendors.currentDialectMetadata

/**
 * Checks if this schema exists or not.
 */
fun Schema.exists(): Boolean = currentDialectMetadata.schemaExists(this)

/**
 * Returns whether [this] table exists in the database.
 *
 * @sample org.jetbrains.exposed.v1.tests.shared.DDLTests.tableExists02
 */
fun Table.exists(): Boolean = currentDialectMetadata.tableExists(this)

/**
 * Returns whether this sequence exists in the database.
 */
fun Sequence.exists(): Boolean = currentDialectMetadata.sequenceExists(this)
