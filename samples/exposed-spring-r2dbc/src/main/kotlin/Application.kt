@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.spring

import org.jetbrains.exposed.v1.core.SqlLogger
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.statements.StatementContext
import org.jetbrains.exposed.v1.core.statements.expandArgs
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.r2dbc.autoconfigure.R2dbcConnectionDetails
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

// Exposed is configured by ExposedReactiveAutoConfiguration, which is applied automatically by the Exposed starter
@SpringBootApplication
class Application {

    // Defining this bean replaces the default database configuration that is provided by ExposedReactiveAutoConfiguration
    @Bean
    fun databaseConfig(connectionDetails: R2dbcConnectionDetails): R2dbcDatabaseConfig.Builder {
        return R2dbcDatabaseConfig {
            // The default logger only logs statements if the DEBUG level is enabled for the logger named 'Exposed'
            sqlLogger = SqlStatementLogger

            // A custom configuration must be told which database is used;
            // Using Spring's connection details means the same database is used as set by `spring.r2dbc.url`
            connectionFactoryOptions {
                from(connectionDetails.connectionFactoryOptions)
            }
            // Otherwise, avoid passing a connectionDetails argument and call `setUrl()` directly
        }
    }
}

// Logs every SQL statement that is executed in a transaction, with its arguments, at the INFO level
object SqlStatementLogger : SqlLogger {
    private val logger = LoggerFactory.getLogger(SqlStatementLogger::class.java)

    override fun log(context: StatementContext, transaction: Transaction) {
        logger.info("SQL: {}", context.expandArgs(transaction))
    }
}

fun main(args: Array<String>) {
    @Suppress("SpreadOperator")
    runApplication<Application>(*args)
}
