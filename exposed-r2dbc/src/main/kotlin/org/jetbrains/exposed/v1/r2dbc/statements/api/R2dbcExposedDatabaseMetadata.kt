package org.jetbrains.exposed.v1.r2dbc.statements.api

import io.r2dbc.spi.IsolationLevel
import org.jetbrains.exposed.v1.core.CheckConstraint
import org.jetbrains.exposed.v1.core.ForeignKeyConstraint
import org.jetbrains.exposed.v1.core.Index
import org.jetbrains.exposed.v1.core.Sequence
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.Version
import org.jetbrains.exposed.v1.core.statements.api.ExposedDatabaseMetadata
import org.jetbrains.exposed.v1.core.vendors.ColumnMetadata
import org.jetbrains.exposed.v1.core.vendors.PrimaryKeyMetadata
import org.jetbrains.exposed.v1.core.vendors.SchemaMetadata

/**
 * Base class responsible for retrieving and storing information about the R2DBC driver and underlying database.
 */
abstract class R2dbcExposedDatabaseMetadata(database: String) : ExposedDatabaseMetadata(database) {
    /** Whether the database supports `ALTER TABLE` with an add column clause. */
    abstract val supportsAlterTableWithAddColumn: Boolean

    /** Whether the database supports `ALTER TABLE` with a drop column clause. */
    abstract val supportsAlterTableWithDropColumn: Boolean

    /** Whether the database supports getting multiple result sets from a single execute. */
    abstract val supportsMultipleResultSets: Boolean

    /** Whether the database supports `SELECT FOR UPDATE` statements. */
    abstract val supportsSelectForUpdate: Boolean

    /** Whether the database supports the `LIMIT` clause with update and delete statements. */
    abstract val supportsLimitWithUpdateOrDelete: Boolean

    /** The version number of the database as a `BigDecimal`. */
    abstract fun getVersion(): Version

    /**  The major version number of the database. */
    abstract fun getMajorVersion(): Int

    /**  The minor version number of the database. */
    abstract fun getMinorVersion(): Int

    /** The name of the database based on the name of the underlying R2DBC driver. */
    abstract fun getDatabaseDialectName(): String

    /** The name of the mode of the database. This is currently applicable only to H2 databases. */
    abstract suspend fun getDatabaseDialectMode(): String?

    /** The version number of the database product as a `String`. */
    abstract fun getDatabaseProductVersion(): String

    /** The default transaction isolation level for the database. */
    abstract suspend fun getDefaultIsolationLevel(): IsolationLevel

    /** A mapping of all schema names in the database to a list of all defined table names in each schema. */
    abstract suspend fun tableNames(): Map<String, List<String>>

    /** A list of existing schema names. */
    abstract suspend fun schemaNames(): List<String>

    /**
     * Returns the current schema name and a list of its existing table names, stored as [SchemaMetadata].
     *
     * A [tableNamesCache] of previously read metadata, if applicable, can be provided to avoid retrieving new metadata.
     */
    abstract suspend fun tableNamesByCurrentSchema(tableNamesCache: Map<String, List<String>>?): SchemaMetadata

    /** Returns a map with the [ColumnMetadata] of all the defined columns in each of the specified [tables]. */
    abstract suspend fun columns(vararg tables: Table): Map<Table, List<ColumnMetadata>>

    /** Returns a map with all the defined indices in each of the specified [tables]. */
    abstract suspend fun existingIndices(vararg tables: Table): Map<Table, List<Index>>

    /** Returns a map with all the defined check constraints in each of the specified [tables]. */
    abstract suspend fun existingCheckConstraints(vararg tables: Table): Map<Table, List<CheckConstraint>>

    /** Returns a map with the [PrimaryKeyMetadata] in each of the specified [tables]. */
    abstract suspend fun existingPrimaryKeys(vararg tables: Table): Map<Table, PrimaryKeyMetadata?>

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
    abstract suspend fun existingSequences(vararg tables: Table): Map<Table, List<Sequence>>

    /** Returns a list of the names of all sequences in the database. */
    abstract suspend fun sequences(): List<String>

    /**
     * Returns a map with the [ForeignKeyConstraint] of all the defined columns in each of the specified [tables],
     * with the table name used as the key.
     */
    abstract suspend fun tableConstraints(tables: List<Table>): Map<String, List<ForeignKeyConstraint>>
}
