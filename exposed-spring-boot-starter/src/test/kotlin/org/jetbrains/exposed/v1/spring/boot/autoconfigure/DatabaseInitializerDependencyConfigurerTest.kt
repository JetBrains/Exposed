package org.jetbrains.exposed.v1.spring.boot.autoconfigure

import org.jetbrains.exposed.v1.spring.boot.Application
import org.jetbrains.exposed.v1.spring.boot.autoconfigure.DatabaseInitializerEarlyInitTest.SchemaVerifierBean
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * Verifies that [ExposedAutoConfiguration] registers Spring Boot's
 * `DatabaseInitializationDependencyConfigurer` itself, so that [DependsOnDatabaseInitialization]
 * ordering works even when no other auto-configuration that would import it
 * (JdbcTemplate, JdbcClient, SQL script initialization) is active.
 */
@SpringBootTest(
    classes = [Application::class, DatabaseInitializerDependencyConfigurerTest.DependencyConfigurerConfig::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:test-dependency-configurer;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.exposed.generate-ddl=true",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration," +
            "org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration," +
            "org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration"
    ]
)
open class DatabaseInitializerDependencyConfigurerTest {

    @TestConfiguration
    open class DependencyConfigurerConfig {
        // The ApplicationContextRunner in ExposedAutoConfigurationTest component-scans this package without the
        // test-type exclude filter, so this bean must stay inactive unless DDL generation is explicitly enabled.
        @Bean
        @DependsOnDatabaseInitialization
        @ConditionalOnProperty("spring.exposed.generate-ddl", havingValue = "true")
        open fun schemaVerifier(): SchemaVerifierBean = SchemaVerifierBean()
    }

    @Autowired
    private lateinit var schemaVerifier: SchemaVerifierBean

    @Test
    fun `schema should be available without other auto-configurations importing the dependency configurer`() {
        assertEquals(
            0L,
            schemaVerifier.tableRowCount,
            "TestTable should be queryable during afterPropertiesSet when only ExposedAutoConfiguration is active"
        )
    }
}
