package org.jetbrains.exposed.v1.spring7.reactive.transaction

import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.reactive.TransactionSynchronizationManager
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicInteger

@ExtendWith(SpringExtension::class)
@ContextConfiguration(
    classes = [
        ExposedAutoConfigurationCopy::class,
        SecondaryTransactionConfig::class,
    ]
)
open class NamedTransactionManagerTest {
    @Autowired
    lateinit var playerService: PlayerService

    @Autowired
    lateinit var recordingTransactionManager: RecordingTransactionManager

    @BeforeEach
    open fun beforeTest() = runTest {
        suspendTransaction {
            SchemaUtils.create(Players)

            Players.insert { }
        }
    }

    @Test
    fun `should not use differently named transaction manager`() = runTest {
        assertEquals(
            1,
            playerService.countPlayers()
        )

        assertEquals(
            0,
            recordingTransactionManager.invocations.get(),
            "recordingTransactionManager was used even though springTransactionManager was requested"
        )
    }
}

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
@EnableExposedReactiveTransactionManagement
class ExposedAutoConfigurationCopy {
    @Bean
    fun cxFactory(): ConnectionFactory = ConnectionFactories.get("r2dbc:h2:mem:///embeddedTest;DB_CLOSE_DELAY=-1;")

    @Bean
    fun springTransactionManager(connectionFactory: ConnectionFactory): SpringReactiveTransactionManager = SpringReactiveTransactionManager(
        connectionFactory,
        R2dbcDatabaseConfig { explicitDialect = H2Dialect() }
    )

    @Bean
    @Primary
    fun exposedSpringTransactionAttributeSource(): ExposedSpringTransactionAttributeSource = ExposedSpringTransactionAttributeSource()
}

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement(proxyTargetClass = true)
@EnableExposedReactiveTransactionManagement
class SecondaryTransactionConfig {

    @Bean
    @Primary
    fun recordingTransactionManager(connectionFactory: ConnectionFactory): RecordingTransactionManager =
        RecordingTransactionManager(connectionFactory)

    @Bean
    fun playerService(): PlayerService = PlayerService()
}

class RecordingTransactionManager(connectionFactory: ConnectionFactory) : R2dbcTransactionManager(connectionFactory) {
    val invocations = AtomicInteger(0)

    override fun doBegin(
        synchronizationManager: TransactionSynchronizationManager,
        transaction: Any,
        definition: TransactionDefinition
    ): Mono<Void> {
        invocations.incrementAndGet()
        return super.doBegin(synchronizationManager, transaction, definition)
    }
}

open class PlayerService {
    @Transactional(transactionManager = "springTransactionManager")
    open suspend fun countPlayers(): Long = Players.selectAll().count()
}

private object Players : LongIdTable("players")
