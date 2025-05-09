package org.jetbrains.exposed.v1.spring.transaction

import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.Test
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.EnableTransactionManagement
import javax.sql.DataSource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

class SpringTransactionSingleConnectionTest {
    object T1 : Table() {
        val c1 = varchar("c1", Int.MIN_VALUE.toString().length)
    }

    val singleConnectionH2TestContainer = AnnotationConfigApplicationContext(SingleConnectionH2TestConfig::class.java)
    val transactionManager = singleConnectionH2TestContainer.getBean(PlatformTransactionManager::class.java)
    val dataSource = singleConnectionH2TestContainer.getBean(DataSource::class.java)

    @BeforeTest
    fun beforeTest() {
        transactionManager.execute {
            SchemaUtils.create(T1)
        }
    }

    @Test
    fun `start transaction with non default isolation level`() {
        transactionManager.execute(
            isolationLevel = TransactionDefinition.ISOLATION_SERIALIZABLE,
        ) {
            T1.selectAll().toList()
        }
    }

    @Test
    fun `nested transaction with non default isolation level`() {
        transactionManager.execute(
            isolationLevel = TransactionDefinition.ISOLATION_SERIALIZABLE,
        ) {
            T1.selectAll().toList()

            // Nested transaction will inherit isolation level from parent transaction because is use the same connection
            transactionManager.execute(
                isolationLevel = TransactionDefinition.ISOLATION_READ_UNCOMMITTED,
            ) {
                DataSourceUtils.getConnection(dataSource).use { connection ->
                    assertEquals(connection.transactionIsolation, TransactionDefinition.ISOLATION_SERIALIZABLE)
                }
                T1.selectAll().toList()
            }
            T1.selectAll().toList()
        }
    }

    @AfterTest
    fun afterTest() {
        transactionManager.execute {
            SchemaUtils.drop(T1)
        }
    }
}

@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
open class SingleConnectionH2TestConfig {

    @Bean
    open fun singleConnectionH2DataSource(): DataSource = SingleConnectionDataSource().apply {
        setDriverClassName("org.h2.Driver")
        setUrl("jdbc:h2:mem:regular;DB_CLOSE_DELAY=-1;")
        setUsername("sa")
        setPassword("")
        setSuppressClose(true)
    }

    @Bean
    open fun singleConnectionH2TransactionManager(
        @Qualifier("singleConnectionH2DataSource") dataSource: DataSource
    ): PlatformTransactionManager = SpringTransactionManager(dataSource = dataSource, DatabaseConfig { useNestedTransactions = true })
}
