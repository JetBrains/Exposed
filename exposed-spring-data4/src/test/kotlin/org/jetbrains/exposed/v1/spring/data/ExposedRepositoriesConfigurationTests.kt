package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import javax.sql.DataSource

@SpringJUnitConfig(ExposedRepositoriesConfigurationTests.CustomTransactionManagerConfiguration::class)
class ExposedRepositoriesConfigurationTests {
    @Autowired
    private lateinit var repository: CustomTransactionManagerRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `repository uses the configured transaction manager`() {
        TransactionTemplate(transactionManager).executeWithoutResult { SchemaUtils.create(Customers) }

        val saved = repository.save(Customer(name = "Ada", email = "ada@example.com"))

        assertEquals(saved, repository.findByIdOrNull(saved.id!!))
    }

    @Configuration
    @EnableTransactionManagement
    @EnableExposedRepositories(
        basePackageClasses = [ExposedRepositoriesConfigurationTests::class],
        considerNestedRepositories = true,
        includeFilters = [
            ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = [CustomTransactionManagerRepository::class]
            )
        ],
        transactionManagerRef = "customTransactionManager"
    )
    open class CustomTransactionManagerConfiguration {
        @Bean
        open fun dataSource(): DataSource = EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .generateUniqueName(true)
            .build()

        @Bean
        open fun customTransactionManager(dataSource: DataSource): PlatformTransactionManager =
            SpringTransactionManager(dataSource)
    }

    @ExposedRepository(table = Customers::class)
    interface CustomTransactionManagerRepository : ExposedCrudRepository<Customer, Long>
}
