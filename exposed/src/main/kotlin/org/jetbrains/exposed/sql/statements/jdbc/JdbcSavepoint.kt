package org.jetbrains.exposed.sql.statements.jdbc

import org.jetbrains.exposed.sql.statements.api.ExposedSavepoint
import java.sql.Savepoint

class JdbcSavepoint(name: String, internal val savepoint: Savepoint) : ExposedSavepoint(name)