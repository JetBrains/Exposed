package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.sql.FieldSet
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.appendTo

interface PostgresSqlReturningDSL : FieldSet {
    fun returning(returning: FieldSet = this)
}

internal class PostgresSqlReturningDSLImpl(
    defaultReturning: FieldSet,
    private val returningSetter: (returningSet: FieldSet) -> Unit
) : PostgresSqlReturningDSL, FieldSet by defaultReturning {

    internal var sqlRenderer: SQLRenderer = NoopSQLRenderer
        private set

    override fun returning(returning: FieldSet) {
        returningSetter(returning)
        sqlRenderer = PostgresqlReturningSQLRenderer(returning)
    }
}

internal class PostgresqlReturningSQLRenderer(
    private val returningColumnSet: FieldSet
) : SQLRenderer {

    override fun render(builder: QueryBuilder) {
        returningColumnSet.realFields.appendTo(prefix = " RETURNING ", builder = builder) {
            it.toQueryBuilder(this)
        }
    }
}