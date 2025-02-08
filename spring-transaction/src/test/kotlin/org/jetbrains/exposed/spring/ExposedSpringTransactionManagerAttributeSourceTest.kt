package org.jetbrains.exposed.spring

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.junit.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * @author ivan@daangn.com
 */
class ExposedSpringTransactionManagerAttributeSourceTest {

    val container = AnnotationConfigApplicationContext(TransactionManagerAttributeSourceTestConfig::class.java)

    @BeforeTest
    fun beforeTest() {
        val testRollback = container.getBean(TestRollback::class.java)
        testRollback.init()
    }

    @Test
    fun `test rollback`() {
        val testRollback = container.getBean(TestRollback::class.java)
        assertFailsWith<ExposedSQLException> {
            testRollback.transaction {
                insertOriginTable()
                insertWrongTable("1234567890")
            }
        }

        assertEquals(0, testRollback.entireTableSize())
    }
}

@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
open class TransactionManagerAttributeSourceTestConfig {

    @Bean
    open fun dataSource(): EmbeddedDatabase = EmbeddedDatabaseBuilder().setName("embeddedTest1").setType(
        EmbeddedDatabaseType.H2
    ).build()

    @Bean
    open fun transactionManager(dataSource: DataSource) = SpringTransactionManager(dataSource)

    @Bean
    open fun transactionAttributeSource() = ExposedSpringTransactionAttributeSource()

    @Bean
    open fun testRollback() = TestRollback()
}

@Transactional
open class TestRollback {

    open fun init() {
        SchemaUtils.create(RollbackTable)
    }

    open fun transaction(block: TestRollback.() -> Unit) {
        block()
    }

    open fun insertOriginTable() {
        RollbackTable.insert {
            it[RollbackTable.name] = "1"
        }
    }

    open fun insertWrongTable(name: String) {
        WrongDefinedRollbackTable.insert {
            it[WrongDefinedRollbackTable.name] = name
        }
    }

    open fun entireTableSize(): Long {
        return RollbackTable.selectAll().count()
    }
}

object RollbackTable : LongIdTable("test_rollback") {
    val name = varchar("name", 5)
}

object WrongDefinedRollbackTable : LongIdTable("test_rollback") {
    val name = varchar("name", 10)
}
