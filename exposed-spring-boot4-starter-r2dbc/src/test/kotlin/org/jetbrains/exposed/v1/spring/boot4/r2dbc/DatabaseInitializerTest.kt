package org.jetbrains.exposed.v1.spring.boot4.r2dbc

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.web.server.context.WebServerInitializedEvent
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.transaction.reactive.TransactionalOperator
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFailsWith

/** Tests that schema is generated for the expected tables & packages. */
@SpringBootTest(
    classes = [Application::class],
    properties = ["spring.autoconfigure.exclude=org.jetbrains.exposed.v1.spring.boot4.r2dbc.autoconfigure.ExposedReactiveAutoConfiguration"]
)
open class DatabaseInitializerTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    suspend fun `should create schema for TestTable and not for IgnoreTable`() {
        assertFailsWith<ExposedR2dbcException> {
            val cxFactory = ConnectionFactories.get("r2dbc:h2:mem:///test-spring;DB_CLOSE_DELAY=-1;")
            val config = R2dbcDatabaseConfig { explicitDialect = H2Dialect() }
            val tempTM = SpringReactiveTransactionManager(cxFactory, config)
            val tempTrxOp = TransactionalOperator.create(tempTM)

            R2dbcDatabase.connect(cxFactory, R2dbcDatabaseConfig { explicitDialect = H2Dialect() })
            suspendTransaction {
                DatabaseInitializer(
                    applicationContext,
                    listOf("org.jetbrains.exposed.v1.spring.boot4.r2dbc.tables.ignore"),
                    tempTrxOp
                )
                    .afterSingletonsInstantiated()
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

/**
 * Tests that [DatabaseInitializer] creates the schema early enough during application start-up.
 *
 * An embedded web server starts to accept requests before any `ApplicationRunner` is invoked. If the schema was only
 * created at that later point, a request that is received in between would fail because its table would not exist yet.
 */
@SpringBootTest(
    classes = [Application::class, DatabaseInitializerStartupTest.StartupProbeConfiguration::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.r2dbc.url=r2dbc:h2:mem:///startup-test;DB_CLOSE_DELAY=-1;",
        "spring.exposed.generate-ddl=true",
    ]
)
@ExtendWith(OutputCaptureExtension::class)
class DatabaseInitializerStartupTest {

    @Autowired
    private lateinit var startupProbe: StartupProbe

    @Test
    fun `schema is created before the web server starts`() {
        assertEquals(true, startupProbe.tableExistedWhenWebServerStarted)
    }

    @Test
    fun `schema is created before application runners are invoked`() {
        assertEquals(true, startupProbe.tableExistedWhenFirstRunnerInvoked)
    }

    @Test
    fun `schema is only created once`(output: CapturedOutput) {
        assertEquals(1, output.all.lines().count { "Schema generation for tables" in it })
    }

    class StartupProbeConfiguration {
        @Bean
        fun startupProbe(connectionFactory: ConnectionFactory): StartupProbe = StartupProbe(connectionFactory)
    }
}

/**
 * Tests that a custom extension of [DatabaseInitializer] that overrides `initializeSchema()` is invoked in the same way:
 * only once during application start-up, and before the embedded web server starts.
 */
@SpringBootTest(
    classes = [Application::class, DatabaseInitializerOverriddenTest.OverridingInitializerConfiguration::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.r2dbc.url=r2dbc:h2:mem:///startup-override-test;DB_CLOSE_DELAY=-1;"]
)
class DatabaseInitializerOverriddenTest {

    @Autowired
    private lateinit var startupProbe: StartupProbe

    @Test
    fun `overridden initializeSchema is only invoked once`() {
        assertEquals(1, CountingDatabaseInitializer.invocations.get())
    }

    @Test
    fun `overridden initializeSchema is invoked before the web server starts`() {
        assertEquals(true, startupProbe.tableExistedWhenWebServerStarted)
    }

    class OverridingInitializerConfiguration {
        @Bean
        fun countingDatabaseInitializer(
            applicationContext: ApplicationContext,
            springTransactionManager: SpringReactiveTransactionManager,
        ): DatabaseInitializer = CountingDatabaseInitializer(
            applicationContext,
            TransactionalOperator.create(springTransactionManager)
        )

        @Bean
        fun startupProbe(connectionFactory: ConnectionFactory): StartupProbe = StartupProbe(connectionFactory)
    }
}

class CountingDatabaseInitializer(
    applicationContext: ApplicationContext,
    transactionalOperator: TransactionalOperator,
) : DatabaseInitializer(applicationContext, emptyList(), transactionalOperator) {
    override suspend fun initializeSchema() {
        invocations.incrementAndGet()
        super.initializeSchema()
    }

    companion object {
        val invocations = AtomicInteger()
    }
}

/**
 * Records whether the expected `TestTable` already exists at 2 points during application start-up:
 * when the embedded web server has started, and when the first `ApplicationRunner` has been invoked.
 */
class StartupProbe(
    connectionFactory: ConnectionFactory
) : ApplicationListener<WebServerInitializedEvent>, ApplicationRunner, Ordered {
    private val databaseClient = DatabaseClient.create(connectionFactory)

    var tableExistedWhenWebServerStarted: Boolean? = null
        private set

    var tableExistedWhenFirstRunnerInvoked: Boolean? = null
        private set

    // Ensures that this runner is invoked before any other ApplicationRunner
    override fun getOrder(): Int = Ordered.HIGHEST_PRECEDENCE

    override fun onApplicationEvent(event: WebServerInitializedEvent) {
        tableExistedWhenWebServerStarted = testTableExists()
    }

    override fun run(args: ApplicationArguments) {
        tableExistedWhenFirstRunnerInvoked = testTableExists()
    }

    private fun testTableExists(): Boolean = databaseClient
        .sql("SELECT COUNT(*) FROM test_table")
        .fetch()
        .first()
        .map { true }
        .onErrorReturn(false)
        .block()
        ?: false
}
