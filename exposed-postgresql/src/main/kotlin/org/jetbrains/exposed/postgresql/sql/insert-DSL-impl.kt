package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.InsertPrepareSQLCustomizer
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.NoopPrepareSQLCustomizer


internal class PostgresReturningDSL(
    internal var returningColumnSet: ColumnSet
) {

    fun returning(returning: ColumnSet = this.returningColumnSet) {
        this.returningColumnSet = returning
    }

}

open class PostgresqlInsertDSL<T : Table>(
    private val table: T,
    private val insertStatement: InsertStatement<*>
) {

    private var onConflictAlreadyCalled = false

    fun values(body: T.(InsertStatement<*>) -> Unit) {
        body(table, insertStatement)
    }

    /**
     * Specifies which conflicts ON CONFLICT takes the alternative action on by choosing arbiter indexes.
     * Either performs unique index inference, or names a constraint explicitly.
     * For ON CONFLICT DO NOTHING, it is optional to specify a conflict_target; when omitted, conflicts with all usable constraints (and unique indexes) are handled.
     *
     *  https://www.postgresql.org/docs/current/sql-insert.html#SQL-ON-CONFLICT
     */
    fun onConflictDoNothing(conflictTarget: String? = null) {
        checkOnConflictNotCalled()
    }

    /**
     * Specifies which conflicts ON CONFLICT takes the alternative action on by choosing arbiter indexes.
     * For ON CONFLICT DO UPDATE, a conflict_target must be provided.
     *
     * https://www.postgresql.org/docs/current/sql-insert.html#SQL-ON-CONFLICT
     */
    fun <T : Comparable<T>> onConflictDoUpdate(idColumn: Column<EntityID<T>>) {
        checkOnConflictNotCalled()
    }

    /**
     * Specifies which conflicts ON CONFLICT takes the alternative action on by choosing arbiter indexes.
     * For ON CONFLICT DO UPDATE, a conflict_target must be provided.
     *
     * https://www.postgresql.org/docs/current/sql-insert.html#SQL-ON-CONFLICT
     */
    fun <T : Comparable<T>> onConflictDoUpdate(constraintName: String) {
        checkOnConflictNotCalled()
    }

    private fun checkOnConflictNotCalled() {
        if (onConflictAlreadyCalled) {
            throw IllegalStateException()
        }
        onConflictAlreadyCalled = true
    }

    internal fun createOnConflictPrepareSQL(): InsertPrepareSQLCustomizer {
        if (onConflictAlreadyCalled) {
            return OnConflictPrepareSQLCallback()
        }

        return NoopPrepareSQLCustomizer
    }
}


class PostgresqlInsertReturningDSL<T : Table>(
    table: T,
    insertStatement: InsertStatement<*>
) : PostgresqlInsertDSL<T>(table, insertStatement) {
    private var returning: ColumnSet = table

    fun returning(returning: ColumnSet = this.returning) {
        this.returning = returning
    }

    internal fun createReturningPrepareSQLCustomizer(): InsertPrepareSQLCustomizer {
        return PostgresqlReturningPrepareSQLCustomizer(returning)
    }
}

class OnConflictPrepareSQLCallback() : InsertPrepareSQLCustomizer {
    override fun afterValuesSet(builder: QueryBuilder) {

    }
}
