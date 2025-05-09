package org.jetbrains.exposed.v1.r2dbc.statements.api

import org.jetbrains.exposed.v1.core.statements.api.ExposedSavepoint

/**
 * Class that stores a reference to a created R2DBC savepoint with the specified [name].
 */
class R2dbcSavepoint(name: String) : ExposedSavepoint(name)
