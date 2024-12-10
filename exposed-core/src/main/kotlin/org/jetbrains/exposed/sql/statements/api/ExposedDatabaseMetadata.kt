package org.jetbrains.exposed.sql.statements.api

import org.jetbrains.exposed.sql.ForeignKeyConstraint
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.vendors.ColumnMetadata
import org.jetbrains.exposed.sql.vendors.PrimaryKeyMetadata
import org.jetbrains.exposed.sql.vendors.SchemaMetadata
import java.math.BigDecimal

/**
 * Base class responsible for retrieving and storing information about the JDBC driver and underlying [database].
 */
abstract class ExposedDatabaseMetadata(val database: String) {
    /** The connection URL for the database. */
    abstract val url: String

    /** The version number of the database as a `BigDecimal`. */
    abstract val version: BigDecimal

    /** The name of the database based on the name of the underlying JDBC driver. */
    abstract val databaseDialectName: String

    /** The version number of the database product as a `String`. */
    abstract val databaseProductVersion: String

    /** The default transaction isolation level for the database. */
    abstract val defaultIsolationLevel: Int

    /** Whether the database supports `ALTER TABLE` with an add column clause. */
    abstract val supportsAlterTableWithAddColumn: Boolean

    /** Whether the database supports `ALTER TABLE` with a drop column clause. */
    abstract val supportsAlterTableWithDropColumn: Boolean

    /** Whether the database supports getting multiple result sets from a single execute. */
    abstract val supportsMultipleResultSets: Boolean

    /** Whether the database supports `SELECT FOR UPDATE` statements. */
    abstract val supportsSelectForUpdate: Boolean

    /** Clears and resets any stored information about the database's current schema to default values. */
    abstract fun resetCurrentScheme()

    /** A mapping of all schema names in the database to a list of all defined table names in each schema. */
    abstract val tableNames: Map<String, List<String>>

    /** A list of existing schema names. */
    abstract val schemaNames: List<String>

    /**
     * Returns the current schema name and a list of its existing table names, stored as [SchemaMetadata].
     *
     * A [tableNamesCache] of previously read metadata, if applicable, can be provided to avoid retrieving new metadata.
     */
    abstract fun tableNamesByCurrentSchema(tableNamesCache: Map<String, List<String>>?): SchemaMetadata

    abstract fun tableNamesForAllSchemas(): List<String>

    /** Returns a map with the [ColumnMetadata] of all the defined columns in each of the specified [tables]. */
    abstract fun columns(vararg tables: Table): Map<Table, List<ColumnMetadata>>

    /** Returns a map with all the defined indices in each of the specified [tables]. */
    abstract fun existingIndices(vararg tables: Table): Map<Table, List<Index>>

    /** Returns a map with the [PrimaryKeyMetadata] in each of the specified [tables]. */
    abstract fun existingPrimaryKeys(vararg tables: Table): Map<Table, PrimaryKeyMetadata?>

    /** Returns a list of the names of all sequences in the database. */
    abstract fun sequences(): List<String>

    /**
     * Returns a map with the [ForeignKeyConstraint] of all the defined columns in each of the specified [tables],
     * with the table name used as the key.
     */
    abstract fun tableConstraints(tables: List<Table>): Map<String, List<ForeignKeyConstraint>>

    /** Clears any cached values. */
    abstract fun cleanCache()

    /** The database-specific and metadata-reliant implementation of [IdentifierManagerApi]. */
    abstract val identifierManager: IdentifierManagerApi
}
