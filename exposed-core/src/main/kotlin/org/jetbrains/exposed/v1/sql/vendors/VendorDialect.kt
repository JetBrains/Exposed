package org.jetbrains.exposed.v1.sql.vendors

import org.jetbrains.exposed.v1.sql.*
import org.jetbrains.exposed.v1.sql.Function
import org.jetbrains.exposed.v1.sql.transactions.CoreTransactionManager

/**
 * Base implementation of a vendor dialect
 */
abstract class VendorDialect(
    override val name: String,
    override val dataTypeProvider: DataTypeProvider,
    override val functionProvider: FunctionProvider
) : DatabaseDialect {

    protected val identifierManager
        @OptIn(InternalApi::class)
        get() = CoreTransactionManager.currentTransaction().db.identifierManager

    @Suppress("UnnecessaryAbstractClass")
    abstract class DialectNameProvider(val dialectName: String)

    override val supportsMultipleGeneratedKeys: Boolean = true

    fun filterCondition(index: Index): String? {
        return index.filterCondition?.let {
            when (currentDialect) {
                is PostgreSQLDialect, is SQLServerDialect, is SQLiteDialect -> {
                    QueryBuilder(false)
                        .append(" WHERE ").append(it)
                        .toString()
                }

                else -> {
                    exposedLogger.warn("Index creation with a filter condition is not supported in ${currentDialect.name}")
                    return null
                }
            }
        } ?: ""
    }

    private fun indexFunctionToString(function: Function<*>): String {
        val baseString = function.toString()
        return when (currentDialect) {
            // SQLite & Oracle do not support "." operator (with table prefix) in index expressions
            is SQLiteDialect, is OracleDialect -> baseString.replace(Regex("""^*[^( ]*\."""), "")
            is MysqlDialect -> if (baseString.first() != '(') "($baseString)" else baseString
            else -> baseString
        }
    }

    /**
     * Uniqueness might be required for foreign key constraints.
     *
     * In PostgreSQL (https://www.postgresql.org/docs/current/indexes-unique.html), UNIQUE means B-tree only.
     * Unique constraints can not be partial
     * Unique indexes can be partial
     */
    override fun createIndex(index: Index): String {
        @OptIn(InternalApi::class)
        val t = CoreTransactionManager.currentTransaction()
        val quotedTableName = t.identity(index.table)
        val quotedIndexName = t.db.identifierManager.cutIfNecessaryAndQuote(index.indexName)
        val keyFields = index.columns.plus(index.functions ?: emptyList())
        val fieldsList = keyFields.joinToString(prefix = "(", postfix = ")") {
            when (it) {
                is Column<*> -> t.identity(it)
                is Function<*> -> indexFunctionToString(it)
                // returned by existingIndices() mapping String metadata to stringLiteral()
                is LiteralOp<*> -> it.value.toString().trim('"')
                else -> {
                    exposedLogger.warn("Unexpected defining key field will be passed as String: $it")
                    it.toString()
                }
            }
        }
        val includesOnlyColumns = index.functions?.isEmpty() != false
        val maybeFilterCondition = filterCondition(index) ?: return ""

        return when {
            // unique and no filter -> constraint, the type is not supported
            index.unique && maybeFilterCondition.isEmpty() && includesOnlyColumns -> {
                "ALTER TABLE $quotedTableName ADD CONSTRAINT $quotedIndexName UNIQUE $fieldsList"
            }
            // unique and filter -> index only, the type is not supported
            index.unique -> {
                "CREATE UNIQUE INDEX $quotedIndexName ON $quotedTableName $fieldsList$maybeFilterCondition"
            }
            // type -> can't be unique or constraint
            index.indexType != null -> {
                createIndexWithType(
                    name = quotedIndexName, table = quotedTableName,
                    columns = fieldsList, type = index.indexType, filterCondition = maybeFilterCondition
                )
            }

            else -> {
                "CREATE INDEX $quotedIndexName ON $quotedTableName $fieldsList$maybeFilterCondition"
            }
        }
    }

    protected open fun createIndexWithType(name: String, table: String, columns: String, type: String, filterCondition: String): String {
        return "CREATE INDEX $name ON $table $columns USING $type$filterCondition"
    }

    override fun dropIndex(tableName: String, indexName: String, isUnique: Boolean, isPartialOrFunctional: Boolean): String {
        return "ALTER TABLE ${identifierManager.quoteIfNecessary(tableName)} DROP CONSTRAINT ${identifierManager.quoteIfNecessary(indexName)}"
    }

    override fun modifyColumn(column: Column<*>, columnDiff: ColumnDiff): List<String> =
        @OptIn(InternalApi::class)
        listOf("ALTER TABLE ${CoreTransactionManager.currentTransaction().identity(column.table)} MODIFY COLUMN ${column.descriptionDdl(true)}")

    override fun addPrimaryKey(table: Table, pkName: String?, vararg pkColumns: Column<*>): String {
        @OptIn(InternalApi::class)
        val transaction = CoreTransactionManager.currentTransaction()
        val columns = pkColumns.joinToString(prefix = "(", postfix = ")") { transaction.identity(it) }
        val constraint = pkName?.let { " CONSTRAINT ${identifierManager.quoteIfNecessary(it)} " } ?: " "
        return "ALTER TABLE ${transaction.identity(table)} ADD${constraint}PRIMARY KEY $columns"
    }
}
