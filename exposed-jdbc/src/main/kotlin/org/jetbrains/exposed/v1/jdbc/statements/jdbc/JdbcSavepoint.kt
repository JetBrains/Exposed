package org.jetbrains.exposed.v1.jdbc.statements.jdbc

import org.jetbrains.exposed.v1.core.statements.api.ExposedSavepoint
import java.sql.Savepoint

/**
 * Class that stores a java.sql.Savepoint object with the specified [name].
 */
class JdbcSavepoint(name: String, internal val savepoint: Savepoint) : ExposedSavepoint(name)
