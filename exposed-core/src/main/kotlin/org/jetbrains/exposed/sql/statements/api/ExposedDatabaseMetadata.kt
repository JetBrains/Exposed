package org.jetbrains.exposed.sql.statements.api

import org.jetbrains.exposed.sql.ForeignKeyConstraint
import org.jetbrains.exposed.sql.Index
import org.jetbrains.exposed.sql.InternalApi
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Sequence
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

    /**  The major version number of the database. */
    abstract val majorVersion: Int

    /**  The minor version number of the database. */
    abstract val minorVersion: Int

    /** The name of the database based on the name of the underlying JDBC driver. */
    abstract val databaseDialectName: String

    /** The name of the mode of the database. This is currently applicable only to H2 databases. */
    abstract val databaseDialectMode: String?

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

    /** Whether the database supports the `LIMIT` clause with update and delete statements. */
    abstract fun supportsLimitWithUpdateOrDelete(): Boolean

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

    /** Returns a map with the [ColumnMetadata] of all the defined columns in each of the specified [tables]. */
    abstract fun columns(vararg tables: Table): Map<Table, List<ColumnMetadata>>

    /** Returns a map with all the defined indices in each of the specified [tables]. */
    abstract fun existingIndices(vararg tables: Table): Map<Table, List<Index>>

    /** Returns a map with the [PrimaryKeyMetadata] in each of the specified [tables]. */
    abstract fun existingPrimaryKeys(vararg tables: Table): Map<Table, PrimaryKeyMetadata?>

    /**
     * Returns a map with all the defined sequences that hold a relation to the specified [tables] in the database.
     *
     * **Note** PostgreSQL is currently the only database that maps relational dependencies for sequences created when
     * a SERIAL column is registered to an `IdTable`. Using this method with any other database returns an empty map.
     *
     * Any sequence created using the CREATE SEQUENCE command will be ignored
     * as it is not necessarily bound to any particular table. Sequences that are used in a table via triggers will also
     * not be returned.
     */
    abstract fun existingSequences(vararg tables: Table): Map<Table, List<Sequence>>

    /** Returns a list of the names of all sequences in the database. */
    abstract fun sequences(): List<String>

    /**
     * Returns a map with the [ForeignKeyConstraint] of all the defined columns in each of the specified [tables],
     * with the table name used as the key.
     */
    abstract fun tableConstraints(tables: List<Table>): Map<String, List<ForeignKeyConstraint>>

    // THIS should become protected after the usage in DatabaseDialect is fully deprecated
    /**
     * Returns the corresponding [ReferenceOption] for the specified [refOption] result,
     * or `null` if the database result is an invalid string without a corresponding match.
     */
    @InternalApi
    abstract fun resolveReferenceOption(refOption: String): ReferenceOption?

    /** Clears any cached values. */
    abstract fun cleanCache()

    /** The database-specific and metadata-reliant implementation of [IdentifierManagerApi]. */
    abstract val identifierManager: IdentifierManagerApi
}
