package org.jetbrains.exposed.v1.spring.boot4.autoconfigure

import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.spring.boot4.Application
import org.jetbrains.exposed.v1.spring.boot4.tables.TestTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Verifies the scenario from EXPOSED-1004: a plain bean with no `@DependsOnDatabaseInitialization` and no
 * `@DependsOn` queries a table during its own initialization and finds the generated schema, because DDL runs
 * while the auto-configured `SpringTransactionManager` bean is created.
 */
@SpringBootTest(
    classes = [Application::class, DatabaseInitializerPlainBeanTest.PlainBeanConfig::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:test-plain-bean;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.exposed.generate-ddl=true"
    ]
)
open class DatabaseInitializerPlainBeanTest {

    @TestConfiguration
    open class PlainBeanConfig {
        // The ApplicationContextRunner in ExposedAutoConfigurationTest component-scans this package without the
        // test-type exclude filter, so this bean must stay inactive unless DDL generation is explicitly enabled.
        @Bean
        @ConditionalOnProperty("spring.exposed.generate-ddl", havingValue = "true")
        open fun plainSchemaVerifier(transactionManager: PlatformTransactionManager): PlainSchemaVerifierBean =
            PlainSchemaVerifierBean(transactionManager)
    }

    class PlainSchemaVerifierBean(
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
    private lateinit var plainSchemaVerifier: PlainSchemaVerifierBean

    @Test
    fun `schema should be available to a plain bean during its initialization`() {
        assertEquals(
            0L,
            plainSchemaVerifier.tableRowCount,
            "TestTable should be queryable during afterPropertiesSet without any ordering annotation"
        )
    }
}
