package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.appendTo
import org.jetbrains.exposed.sql.statements.InsertPrepareSQLCustomizer

internal class PostgresqlReturningPrepareSQLCustomizer(
    private val returningColumnSet: ColumnSet
) : InsertPrepareSQLCustomizer {

    override fun afterValuesSet(builder: QueryBuilder) {
        returningColumnSet.realFields.appendTo(prefix = " RETURNING ", builder = builder) {
            it.toQueryBuilder(this)
        }
    }
}
