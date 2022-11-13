package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.vendors.H2Dialect
import org.jetbrains.exposed.sql.vendors.H2FunctionProvider
import org.jetbrains.exposed.sql.vendors.h2Mode

open class BatchReplaceStatement(
    table: Table,
    shouldReturnGeneratedValues: Boolean = true
) : BaseBatchInsertStatement(table, ignore = false, shouldReturnGeneratedValues) {
    override fun prepareSQL(transaction: Transaction): String {
        val dialect = transaction.db.dialect
        val functionProvider = when (dialect.h2Mode) {
            // https://github.com/h2database/h2database/issues/2007
            H2Dialect.H2CompatibilityMode.PostgreSQL -> H2FunctionProvider
            else -> dialect.functionProvider
        }
        return functionProvider.replace(table, arguments!!.first(), transaction)
    }
}
