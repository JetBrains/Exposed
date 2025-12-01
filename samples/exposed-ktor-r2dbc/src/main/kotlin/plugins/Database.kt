@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.plugins

import io.ktor.server.application.*
import io.r2dbc.spi.ConnectionFactoryOptions
import io.r2dbc.spi.IsolationLevel
import org.jetbrains.exposed.samples.r2dbc.domain.comment.Comments
import org.jetbrains.exposed.samples.r2dbc.domain.issue.Issues
import org.jetbrains.exposed.samples.r2dbc.domain.project.Projects
import org.jetbrains.exposed.samples.r2dbc.domain.user.Users
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

suspend fun Application.configureDatabase() {
    val config = environment.config.config("database")
    val dbName = config.property("name").getString()
    val dbUser = config.property("user").getString()

    val database = R2dbcDatabase.connect(
        url = "r2dbc:postgresql://db:5432/$dbName",
        databaseConfig = R2dbcDatabaseConfig {
            defaultMaxAttempts = 1
            defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED

            connectionFactoryOptions {
                option(ConnectionFactoryOptions.USER, dbUser)
            }
        }
    )

    suspendTransaction(db = database) {
        SchemaUtils.drop(Projects, Users, Issues, Comments)
        SchemaUtils.create(Projects, Users, Issues, Comments)
    }
}
