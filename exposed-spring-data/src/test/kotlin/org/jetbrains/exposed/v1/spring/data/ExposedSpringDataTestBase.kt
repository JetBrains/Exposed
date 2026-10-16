package org.jetbrains.exposed.v1.spring.data

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import javax.sql.DataSource

/**
 * Base class for the Spring Data tests.
 *
 * All subclasses share [SpringDataExposedTestConfiguration], so Spring caches and reuses a single context.
 * That context registers one repository per test class (every top-level repository interface in this package).
 * The [Customers] table is created before and dropped after every test.
 */
@SpringJUnitConfig(SpringDataExposedTestConfiguration::class)
@Transactional
abstract class ExposedSpringDataTestBase {

    @Autowired
    lateinit var tm: PlatformTransactionManager

    /** The repository under test, also used by [seed]. */
    protected abstract val repository: ExposedCrudRepository<Customer, Long>

    @BeforeEach
    fun createTables() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.create(Customers) }

    @AfterEach
    fun dropTables() = TransactionTemplate(tm).executeWithoutResult { SchemaUtils.drop(Customers) }

    protected fun seed(): List<Customer> = repository.saveAll(
        listOf(
            Customer(name = "Ann", email = "ann@x.io", age = 25, active = true, nickname = "annie", createdAt = JAN),
            Customer(name = "Bob", email = "bob@x.io", age = 30, active = false, nickname = null, createdAt = FEB),
            Customer(name = "Carla", email = "carla@example.com", age = 35, active = true, nickname = "car_la", createdAt = MAR),
            Customer(name = "Dan", email = "dan@x.io", age = 40, active = false, nickname = "50%off", createdAt = APR),
        )
    )

    /** Names sorted alphabetically, for results without a defined order. */
    protected fun Iterable<Customer>.names() = map { it.name }.sorted()

    /** Names in result order, for results with a defined order. */
    protected fun Iterable<Customer>.orderedNames() = map { it.name }

    companion object {
        val JAN: Instant = Instant.parse("2024-01-01T00:00:00Z")
        val FEB: Instant = Instant.parse("2024-02-01T00:00:00Z")
        val MAR: Instant = Instant.parse("2024-03-01T00:00:00Z")
        val APR: Instant = Instant.parse("2024-04-01T00:00:00Z")
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
    val age = integer("age")
    val active = bool("active")
    val nickname = varchar("nickname", 255).nullable()
    val createdAt = timestamp("created_at")
}

data class Customer(
    val id: Long? = null,
    val name: String,
    val email: String,
    val age: Int = 0,
    val active: Boolean = true,
    val nickname: String? = null,
    val createdAt: Instant = Instant.parse("2024-01-01T00:00:00Z"),
)
