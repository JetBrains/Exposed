package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import javax.sql.DataSource

@SpringJUnitConfig(SpringDataExposedTestConfiguration::class)
@Transactional
open class ExposedSpringDataTest {

    @Autowired
    lateinit var customerRepository: CustomerRepository

    @Autowired
    lateinit var tm: PlatformTransactionManager

    @BeforeEach
    fun setUp() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.create(Customers) }

    @AfterEach
    fun tearDown() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.drop(Customers) }


    @Test
    fun testSaveAssignsIdAndFindByIdReturnsIt() {
        val saved = customerRepository.save(Customer(name = "Ann", email = "ann@x.io"))
        assertNotNull(saved.id)
        assertEquals(saved, customerRepository.findByIdOrNull(saved.id))
    }

    @Test
    fun testFindByEmail() {
        customerRepository.save(Customer(name = "Ann", email = "ann@x.io"))
        assertEquals("Ann", customerRepository.findByEmail("ann@x.io")?.name)
        assertNull(customerRepository.findByEmail("nobody@x.io"))
    }

    @Test
    fun testFindByIdReturnsNullWhenMissing() {
        assertNull(customerRepository.findByIdOrNull(42L))
    }
}

@Configuration
@EnableExposedRepositories
@EnableTransactionManagement
open class SpringDataExposedTestConfiguration {
    @Bean
    open fun dataSource(): DataSource = EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build()

    @Bean
    open fun transactionManager(dataSource: DataSource): PlatformTransactionManager =
        SpringTransactionManager(dataSource)
}

object Customers : LongIdTable("customers") {
    val name = varchar("name", 255)
    val email = varchar("email", 255).uniqueIndex()
}

data class Customer(
    val id: Long? = null,
    val name: String,
    val email: String,
)

@ExposedRepository(table = Customers::class)
interface CustomerRepository : ExposedCrudRepository<Customer, Long> {
    fun findByEmail(email: String): Customer?
}

