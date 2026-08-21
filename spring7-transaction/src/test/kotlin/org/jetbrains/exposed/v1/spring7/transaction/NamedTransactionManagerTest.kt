package org.jetbrains.exposed.v1.spring7.transaction

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

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
    open fun beforeTest() {
        transaction {
            SchemaUtils.create(Players)

            Players.insert { }
        }
    }

    @Test
    fun `should not use differently named transaction manager`() {
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
class ExposedAutoConfigurationCopy {
    @Bean
    fun dataSource(): EmbeddedDatabase = EmbeddedDatabaseBuilder().setName(
        "embeddedTest"
    ).setType(EmbeddedDatabaseType.H2).build()

    @Bean
    fun springTransactionManager(dataSource: DataSource): SpringTransactionManager = SpringTransactionManager(dataSource)

    @Bean
    @Primary
    fun exposedSpringTransactionAttributeSource(): ExposedSpringTransactionAttributeSource = ExposedSpringTransactionAttributeSource()
}

@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement(proxyTargetClass = true)
class SecondaryTransactionConfig {

    @Bean @Primary
    fun recordingTransactionManager(dataSource: DataSource): RecordingTransactionManager =
        RecordingTransactionManager(dataSource)

    @Bean
    fun playerService(): PlayerService = PlayerService()
}

class RecordingTransactionManager(dataSource: DataSource) : DataSourceTransactionManager(dataSource) {
    val invocations = AtomicInteger(0)

    override fun doBegin(transaction: Any, definition: TransactionDefinition) {
        invocations.incrementAndGet()
        super.doBegin(transaction, definition)
    }
}

open class PlayerService {
    @Transactional(transactionManager = "springTransactionManager")
    open fun countPlayers(): Long = Players.selectAll().count()
}

private object Players : LongIdTable("players")
