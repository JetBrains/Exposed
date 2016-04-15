package org.jetbrains.exposed.spring

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.junit.Assert
import org.junit.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.TransactionManagementConfigurer
import org.springframework.transaction.annotation.Transactional
import javax.sql.DataSource


private val ctx = AnnotationConfigApplicationContext(TestConfig::class.java)

@Transactional
class ExposedTransactionManagerTest {

    @Test
    fun testConnection() {

        try {
            val pm = ctx.getBean(PlatformTransactionManager::class.java)
            if(pm !is ExposedTransactionManager) error("Wrong txManager instance: ${pm.javaClass.name}")

            val t1 =  object : Table() {
                val c1 = varchar("c1", 5).nullable()
            }

            SchemaUtils.create(t1)
            t1.insert {
                it[c1] = "112"
            }

            Assert.assertEquals(t1.selectAll().count(), 1)

        } finally {
            (ctx.getBean(DataSource::class.java) as EmbeddedDatabase).shutdown()
        }

    }

}

@Configuration
@EnableTransactionManagement
open class TestConfig : TransactionManagementConfigurer {

    @Bean
    open fun ds() = EmbeddedDatabaseBuilder().setName("embeddedTest").setType(EmbeddedDatabaseType.H2).build()

    @Bean
    override fun annotationDrivenTransactionManager(): PlatformTransactionManager? = ExposedTransactionManager(ds())

}