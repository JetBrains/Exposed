package org.jetbrains.exposed.postgresql.sql

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.FieldSet
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.InsertPrepareSQLRenderer
import org.jetbrains.exposed.sql.statements.InsertStatement
import org.jetbrains.exposed.sql.statements.NoopPrepareSQLCustomizer


open class PostgresqlInsertDSL<T : Table>(
    private val table: T,
    private val insertStatement: InsertStatement<*>
) {

    private var onConflictAlreadyCalled = false
    private var onConflictDo: InsertPrepareSQLRenderer = NoopPrepareSQLCustomizer

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
    fun onConflictDoNothing(vararg conflictTarget: Column<*>) {
        checkOnConflictNotCalled()
        onConflictDo = OnConflictDoNothingSqlRenderer(columns = conflictTarget, constraint = null)
    }

    /**
     * See [onConflictDoNothing]
     */
    fun onConflictDoNothingConstraint(constraint: String) {
        checkOnConflictNotCalled()
        onConflictDo = OnConflictDoNothingSqlRenderer(columns = null, constraint = constraint)
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

    internal fun createOnConflictPrepareSQL(): InsertPrepareSQLRenderer {
        return onConflictDo
    }
}


class PostgresqlInsertReturningDSL<T : Table>(
    table: T,
    insertStatement: InsertStatement<*>
) : PostgresqlInsertDSL<T>(table, insertStatement) {
    private var returning: FieldSet = table

    fun returning(returning: FieldSet = this.returning) {
        this.returning = returning
    }

    internal fun createReturningPrepareSQLCustomizer(): InsertPrepareSQLRenderer {
        return PostgresqlReturningSQLRenderer(returning)
    }
}

class OnConflictDoNothingSqlRenderer(
    private val constraint: String? = null,
    columns: Array<out Column<*>>? = null
) : InsertPrepareSQLRenderer {
    private val conflictTargetColumnSet = if (columns?.isEmpty() == true) null else columns

    init {
        check(constraint == null || constraint.isNotBlank()) {
            "conflictTarget can't be blank string"
        }
    }

    override fun afterValuesSet(builder: QueryBuilder) {
        builder {
            append(" ON CONFLICT")
            if (constraint != null) {
                append(" ON CONSTRAINT $constraint")
            }
            conflictTargetColumnSet?.appendTo(prefix = " (", postfix = ")") { column ->
                append(column.name)
            }
            append(" DO NOTHING")
        }
    }
}

class OnConflictUpdateSqlRenderer() : InsertPrepareSQLRenderer {
    override fun afterValuesSet(builder: QueryBuilder) {

    }
}