package org.jetbrains.exposed.v1.spring.transaction

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * @author ivan@daangn.com
 */
class SpringTransactionRollbackTest {

    val container = AnnotationConfigApplicationContext(TransactionManagerAttributeSourceTestConfig::class.java)

    @BeforeEach
    fun beforeTest() {
        val testRollback = container.getBean(TestRollback::class.java)
        testRollback.init()
    }

    @Test
    fun `test ExposedSQLException rollback`() {
        val testRollback = container.getBean(TestRollback::class.java)
        assertFailsWith<ExposedSQLException> {
            testRollback.transaction {
                insertOriginTable()
                insertWrongTable("1234567890")
            }
        }

        assertEquals(0, testRollback.entireTableSize())
    }

    @Test
    fun `test RuntimeException rollback`() {
        val testRollback = container.getBean(TestRollback::class.java)
        assertFailsWith<RuntimeException> {
            testRollback.transaction {
                insertOriginTable()
                @Suppress("TooGenericExceptionThrown")
                throw RuntimeException()
            }
        }

        assertEquals(0, testRollback.entireTableSize())
    }

    @Test
    fun `test check exception commit`() {
        val testRollback = container.getBean(TestRollback::class.java)
        assertFailsWith<Exception> {
            testRollback.transaction {
                insertOriginTable()
                @Suppress("TooGenericExceptionThrown")
                throw Exception()
            }
        }

        assertEquals(1, testRollback.entireTableSize())
    }

    @AfterEach
    fun afterTest() {
        container.close()
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
        RollbackTable.deleteAll()
    }

    open fun transaction(block: TestRollback.() -> Unit) {
        block()
    }

    open fun insertOriginTable() {
        RollbackTable.insert {
            it[name] = "1"
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
