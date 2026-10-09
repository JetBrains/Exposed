package org.jetbrains.exposed.v1.spring.boot4.r2dbc

import io.r2dbc.spi.ConnectionFactories
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.ExposedR2dbcException
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.spring.boot4.r2dbc.tables.TestTable
import org.jetbrains.exposed.v1.spring.boot4.r2dbc.tables.ignore.IgnoreTable
import org.jetbrains.exposed.v1.spring7.reactive.transaction.SpringReactiveTransactionManager
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.transaction.reactive.TransactionalOperator
import kotlin.test.assertFailsWith

@SpringBootTest(
    classes = [Application::class],
    properties = ["spring.autoconfigure.exclude=org.jetbrains.exposed.v1.spring.boot4.r2dbc.autoconfigure.ExposedReactiveAutoConfiguration"]
)
open class DatabaseInitializerTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `should create schema for TestTable and not for IgnoreTable`() = runTest {
        assertFailsWith<ExposedR2dbcException> {
            val cxFactory = ConnectionFactories.get("r2dbc:h2:mem:///test-spring;DB_CLOSE_DELAY=-1;")
            val config = R2dbcDatabaseConfig { explicitDialect = H2Dialect() }
            val tempTM = SpringReactiveTransactionManager(cxFactory, config)
            val tempTrxOp = TransactionalOperator.create(tempTM)

            R2dbcDatabase.connect(cxFactory, R2dbcDatabaseConfig { explicitDialect = H2Dialect() })
            suspendTransaction {
                val noArgs = DefaultApplicationArguments()
                DatabaseInitializer(
                    applicationContext,
                    listOf("org.jetbrains.exposed.v1.spring.boot4.r2dbc.tables.ignore"),
                    tempTrxOp
                )
                    .run(noArgs)
                Assertions.assertEquals(0L, TestTable.selectAll().count())
                IgnoreTable.selectAll().count()
            }
        }
    }

    @Test
    fun `ignore non object Table`() {
        R2dbcDatabase.connect("r2dbc:h2:mem:///test-spring;DB_CLOSE_DELAY=-1;", user = "sa", driver = "h2")
        val tables = discoverExposedTables(applicationContext, listOf())
        Assertions.assertEquals(2, tables.size)
        assert(TestTable in tables && IgnoreTable in tables)
    }
}
