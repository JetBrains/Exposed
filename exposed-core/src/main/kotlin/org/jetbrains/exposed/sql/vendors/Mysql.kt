package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.math.BigDecimal


internal object MysqlDataTypeProvider : DataTypeProvider() {
    override fun dateTimeType(): String = if ((currentDialect as MysqlDialect).isFractionDateTimeSupported()) "DATETIME(6)" else "DATETIME"

    override fun binaryType(): String {
        exposedLogger.error("The length of the Binary column is missing.")
        error("The length of the Binary column is missing.")
    }
}

internal open class MysqlFunctionProvider : FunctionProvider() {
    internal object INSTANSE : MysqlFunctionProvider()

    private object CharColumnType : StringColumnType() {
        override fun sqlType(): String = "CHAR"
    }

    override fun cast(expr: Expression<*>, type: IColumnType, builder: QueryBuilder) = when (type) {
        is StringColumnType -> super.cast(expr, CharColumnType, builder)
        else -> super.cast(expr, type, builder)
    }

    override fun random(seed: Int?) = "RAND(${seed?.toString().orEmpty()})"

    override fun <T : String?> ExpressionWithColumnType<T>.match(pattern: String, mode: MatchMode?): Op<Boolean> = MATCH(this, pattern, mode ?: MysqlMatchMode.STRICT)

    override fun replace(table: Table, data: List<Pair<Column<*>, Any?>>, transaction: Transaction): String {
        val builder = QueryBuilder(true)
        val columns = data.joinToString { transaction.identity(it.first) }
        val values = builder.apply { data.appendTo { registerArgument(it.first.columnType, it.second) } }.toString()
        return "REPLACE INTO ${transaction.identity(table)} ($columns) VALUES ($values)"
    }

    override val DEFAULT_VALUE_EXPRESSION: String = "() VALUES ()"

    override fun insert(ignore: Boolean, table: Table, columns: List<Column<*>>, expr: String, transaction: Transaction): String {
        val def = super.insert(false, table, columns, expr, transaction)
        return if (ignore) def.replaceFirst("INSERT", "INSERT IGNORE") else def
    }

    override fun delete(ignore: Boolean, table: Table, where: String?, limit: Int?, transaction: Transaction): String {
        val def = super.delete(false, table, where, limit, transaction)
        return if (ignore) def.replaceFirst("DELETE", "DELETE IGNORE") else def
    }

    override fun <T : String?> regexp(expr1: Expression<T>, pattern: Expression<String>, caseSensitive: Boolean, queryBuilder: QueryBuilder) {
        return if((currentDialect as MysqlDialect).isMysql8)
            super.regexp(expr1, pattern, caseSensitive, queryBuilder)
        else
            queryBuilder { append(expr1, " REGEXP ", pattern)}
    }

    private class MATCH(val expr: ExpressionWithColumnType<*>, val pattern: String, val mode: MatchMode) : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
            append("MATCH(", expr, ") AGAINST ('", pattern, "' ", mode.mode(),")")
        }
    }

    private enum class MysqlMatchMode(val operator: String) : MatchMode {
        STRICT("IN BOOLEAN MODE"),
        NATURAL_LANGUAGE("IN NATURAL LANGUAGE MODE");

        override fun mode() = operator
    }
}

open class MysqlDialect : VendorDialect(dialectName, MysqlDataTypeProvider, MysqlFunctionProvider.INSTANSE) {

    override fun isAllowedAsColumnDefault(e: Expression<*>): Boolean {
        if (super.isAllowedAsColumnDefault(e)) return true
        val acceptableDefaults = arrayOf("CURRENT_TIMESTAMP", "CURRENT_TIMESTAMP()", "NOW()", "CURRENT_TIMESTAMP(6)", "NOW(6)")
        return e.toString().trim() in acceptableDefaults && isFractionDateTimeSupported()
    }

    override fun fillConstraintCacheForTables(tables: List<Table>) {
        val allTableNames = tables.map { it.nameInDatabaseCase() }
        val inTableList = allTableNames.joinToString("','", prefix = " ku.TABLE_NAME IN ('", postfix = "')")
        val tr = TransactionManager.current()
        val schemaName = "'${getDatabase()}'"
        val constraintsToLoad = HashMap<String, MutableList<ForeignKeyConstraint>>()
        tr.exec(
                "SELECT\n" +
                        "  rc.CONSTRAINT_NAME,\n" +
                        "  ku.TABLE_NAME,\n" +
                        "  ku.COLUMN_NAME,\n" +
                        "  ku.REFERENCED_TABLE_NAME,\n" +
                        "  ku.REFERENCED_COLUMN_NAME,\n" +
                        "  rc.UPDATE_RULE,\n" +
                        "  rc.DELETE_RULE\n" +
                        "FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc\n" +
                        "  INNER JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE ku\n" +
                        "    ON ku.TABLE_SCHEMA = rc.CONSTRAINT_SCHEMA AND rc.CONSTRAINT_NAME = ku.CONSTRAINT_NAME\n" +
                        "WHERE ku.TABLE_SCHEMA = $schemaName " +
                        "   AND ku.CONSTRAINT_SCHEMA = $schemaName" +
                        "   AND rc.CONSTRAINT_SCHEMA = $schemaName" +
                        "   AND $inTableList") { rs ->
            while (rs.next()) {
                val fromTableName = rs.getString("TABLE_NAME")!!
                if (fromTableName !in allTableNames) continue
                val fromColumnName = rs.getString("COLUMN_NAME")!!.quoteIdentifierWhenWrongCaseOrNecessary(tr)
                val constraintName = rs.getString("CONSTRAINT_NAME")!!
                val targetTableName = rs.getString("REFERENCED_TABLE_NAME")!!
                val targetColumnName = rs.getString("REFERENCED_COLUMN_NAME")!!.quoteIdentifierWhenWrongCaseOrNecessary(tr)
                val constraintUpdateRule = ReferenceOption.valueOf(rs.getString("UPDATE_RULE")!!.replace(" ", "_"))
                val constraintDeleteRule = ReferenceOption.valueOf(rs.getString("DELETE_RULE")!!.replace(" ", "_"))
                constraintsToLoad.getOrPut(fromTableName) { arrayListOf() }.add(
                        ForeignKeyConstraint(constraintName,
                                targetTableName, targetColumnName,
                                fromTableName, fromColumnName,
                                constraintUpdateRule, constraintDeleteRule)
                )
            }

            columnConstraintsCache.putAll(constraintsToLoad)
        }
    }

    override fun dropIndex(tableName: String, indexName: String): String = "ALTER TABLE $tableName DROP INDEX $indexName"

    fun isFractionDateTimeSupported() = TransactionManager.current().db.isVersionCovers(BigDecimal("5.6"))

    internal val isMysql8 by lazy {
        TransactionManager.current().db.isVersionCovers(BigDecimal("8.0"))
    }

    companion object {
        const val dialectName = "mysql"
    }
}
