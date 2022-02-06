package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.sql.FieldSet
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.appendTo
import org.jetbrains.exposed.sql.statements.InsertPrepareSQLRenderer

internal class PostgresqlReturningSQLRenderer(
    private val returningColumnSet: FieldSet
) : InsertPrepareSQLRenderer {

    override fun afterValuesSet(builder: QueryBuilder) {
        returningColumnSet.realFields.appendTo(prefix = " RETURNING ", builder = builder) {
            it.toQueryBuilder(this)
        }
    }
}
