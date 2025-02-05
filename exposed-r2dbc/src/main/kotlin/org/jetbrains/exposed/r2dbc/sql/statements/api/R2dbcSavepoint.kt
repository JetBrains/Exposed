package org.jetbrains.exposed.r2dbc.sql.statements.api

import org.jetbrains.exposed.sql.statements.api.ExposedSavepoint

/**
 * Class that stores a reference to a created R2DBC savepoint with the specified [name].
 */
class R2dbcSavepoint(name: String) : ExposedSavepoint(name)
