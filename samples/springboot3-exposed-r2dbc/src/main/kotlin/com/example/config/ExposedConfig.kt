package com.example.config

import io.r2dbc.spi.ConnectionFactory
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.spring.boot.autoconfigure.ExposedAutoConfiguration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ImportAutoConfiguration(
    exclude = [DataSourceTransactionManagerAutoConfiguration::class, ExposedAutoConfiguration::class]
)
internal class ExposedConfig {

    private final val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    @Bean
    internal fun r2dbcDatabase(connectFactory: ConnectionFactory): R2dbcDatabase {
        logger.info("Connecting to connectFactory: ${connectFactory.metadata}")
        return R2dbcDatabase.connect(
            connectionFactory = connectFactory,
            databaseConfig = R2dbcDatabaseConfig {
                useNestedTransactions = true
                // org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
                explicitDialect = MysqlDialect()
            }
        )
    }
}
