package org.jetbrains.exposed.sql.statements.jdbc

import org.jetbrains.exposed.sql.statements.api.ExposedSavepoint
import java.sql.Savepoint

/**
 * Class that stores a java.sql.Savepoint object with the specified [name].
 */
class JdbcSavepoint(name: String, internal val savepoint: Savepoint) : ExposedSavepoint(name)
