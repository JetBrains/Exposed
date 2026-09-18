package org.jetbrains.exposed.v1.spring.boot.autoconfigure

import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.spring.boot.Application
import org.jetbrains.exposed.v1.spring.boot.DatabaseInitializer
import org.jetbrains.exposed.v1.spring.boot.tables.TestTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * @author zbqmgldjfh@gmail.com
 * Verifies that [DatabaseInitializer] is registered as a database initializer via Spring Boot's
 * [DatabaseInitializerDetector][org.springframework.boot.sql.init.dependency.DatabaseInitializerDetector] SPI,
 * so that beans annotated with [DependsOnDatabaseInitialization] automatically wait for DDL to complete
 * without requiring explicit `@DependsOn("databaseInitializer")`.
 */
@SpringBootTest(
    classes = [Application::class, DatabaseInitializerEarlyInitTest.EarlyInitConfig::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:test-early-init;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.exposed.generate-ddl=true"
    ]
)
open class DatabaseInitializerEarlyInitTest {

    @TestConfiguration
    open class EarlyInitConfig {
        @Bean
        @DependsOnDatabaseInitialization
        @ConditionalOnProperty("spring.exposed.generate-ddl", havingValue = "true")
        open fun schemaVerifier(transactionManager: PlatformTransactionManager): SchemaVerifierBean =
            SchemaVerifierBean(transactionManager)
    }

    /**
     * A bean that queries the TestTable during its own initialization.
     * Uses [DependsOnDatabaseInitialization] (Spring Boot standard) instead of
     * `@DependsOn("databaseInitializer")` to verify SPI-based automatic ordering.
     */
    class SchemaVerifierBean(
        private val transactionManager: PlatformTransactionManager
    ) : InitializingBean {
        var tableRowCount: Long = -1

        override fun afterPropertiesSet() {
            TransactionTemplate(transactionManager).execute {
                tableRowCount = TestTable.selectAll().count()
            }
        }
    }

    @Autowired
    private lateinit var schemaVerifier: SchemaVerifierBean

    @Test
    fun `schema should be available during bean initialization via SPI`() {
        assertEquals(
            0L,
            schemaVerifier.tableRowCount,
            "TestTable should be queryable during afterPropertiesSet with @DependsOnDatabaseInitialization"
        )
    }

    @Test
    fun `DatabaseInitializer should be an InitializingBean`() {
        assertTrue(
            InitializingBean::class.java.isAssignableFrom(DatabaseInitializer::class.java),
            "DatabaseInitializer should implement InitializingBean"
        )
    }
}
