package org.jetbrains.exposed.spring

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.TransactionManagementConfigurer
import org.springframework.transaction.annotation.Transactional


@Configuration
@EnableTransactionManagement/*(mode = AdviceMode.ASPECTJ, proxyTargetClass = true)*/
open class TestConfig : TransactionManagementConfigurer {

    @Bean
    open fun ds() = EmbeddedDatabaseBuilder().setName("embeddedTest").setType(EmbeddedDatabaseType.H2).build()

    @Bean
    override fun annotationDrivenTransactionManager(): PlatformTransactionManager? = SpringTransactionManager(ds())

}

@RunWith(SpringJUnit4ClassRunner::class)
@ContextConfiguration(classes = arrayOf(TestConfig::class))
@Transactional
open class ExposedTransactionManagerTest {

    @Autowired
    lateinit var ctx: ApplicationContext

    @Test
    fun testConnection() {
        val pm = ctx.getBean(PlatformTransactionManager::class.java)
        if(pm !is SpringTransactionManager) error("Wrong txManager instance: ${pm.javaClass.name}")

        val t1 =  object : Table() {
            val c1 = varchar("c1", 5).nullable()
        }

        SchemaUtils.create(t1)
        t1.insert {
            it[c1] = "112"
        }

        Assert.assertEquals(t1.selectAll().count(), 1)

    }

}