package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.sql.FieldSet
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.appendTo
import org.jetbrains.exposed.sql.render.NoopSQLRenderer
import org.jetbrains.exposed.sql.render.SQLRenderer

interface PostgresSqlReturningDSL : FieldSet {
    fun returning(returning: FieldSet = this)
}

internal class PostgresSqlReturningDSLImpl(
    defaultReturning: FieldSet
) : PostgresSqlReturningDSL, FieldSet by defaultReturning {

    private var _sqlRenderer: SQLRenderer = NoopSQLRenderer
    internal val sqlRenderer
        get() = _sqlRenderer

    override fun returning(returning: FieldSet) {
        _sqlRenderer = PostgresqlReturningSQLRenderer(returning)
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