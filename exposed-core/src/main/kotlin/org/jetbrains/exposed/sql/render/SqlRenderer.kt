package org.jetbrains.exposed.sql.render

import org.jetbrains.exposed.sql.QueryBuilder

/**
 * Render callbacks for insert statement - order of invoked callbacks may differ per SQL dialect.
 * Some may not be invoked at all in dialect.
 */
interface RenderInsertSQLCallbacks {
    /**
     * Render `RETURNING ...` statement - for Postgresql
     */
    fun returning(builder: QueryBuilder) {}

    /**
     * Render `ON CONFLICT ...` statement - for Postgresql
     */
    fun onConflict(builder: QueryBuilder) {}

    object Noop : RenderInsertSQLCallbacks
}

interface RenderUpdateSQLCallbacks {
    /**
     * Render `RETURNING ...` statement - for Postgresql
     */
    fun returning(builder: QueryBuilder) {}

    object Noop : RenderUpdateSQLCallbacks
}

interface RenderDeleteSQLCallbacks {
    /**
     * Render `RETURNING ...` statement - for Postgresql
     */
    fun returning(builder: QueryBuilder) {}

    object Noop : RenderDeleteSQLCallbacks
}