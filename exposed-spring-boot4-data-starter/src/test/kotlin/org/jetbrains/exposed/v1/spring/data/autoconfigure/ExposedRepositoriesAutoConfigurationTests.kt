package org.jetbrains.exposed.v1.spring.data.autoconfigure

import org.jetbrains.exposed.v1.spring.boot4.autoconfigure.ExposedAutoConfiguration
import org.jetbrains.exposed.v1.spring.data.EnableExposedRepositories
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType
import org.springframework.transaction.PlatformTransactionManager
import java.util.function.Supplier
import javax.sql.DataSource

@SpringBootTest(
    classes = [StarterApplication::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:exposed_spring_data_starter;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.exposed.generate-ddl=true"
    ]
)
open class ExposedRepositoriesStarterIntegrationTests {
    @Autowired
    private lateinit var repository: CustomerRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `repositories are auto-configured and use the Exposed transaction manager`() {
        val saved = repository.save(Customer(name = "Ada"))

        assertNotNull(saved.id)
        assertEquals(saved, repository.findByIdOrNull(saved.id!!))
        assertTrue(transactionManager is SpringTransactionManager)
    }
}

@SpringBootTest(
    classes = [StarterApplication::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:exposed_spring_data_starter_lazy;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.exposed.generate-ddl=true",
        "spring.data.exposed.repositories.bootstrap-mode=lazy"
    ]
)
open class ExposedRepositoriesStarterLazyBootstrapTests {
    @Autowired
    private lateinit var repository: CustomerRepository

    @Test
    fun `lazy repositories are usable`() {
        val saved = repository.save(Customer(name = "Grace"))

        assertEquals(saved, repository.findByIdOrNull(saved.id!!))
    }
}

class ExposedRepositoriesAutoConfigurationTests {
    @Test
    fun `repositories can be disabled`() {
        testRunner
            .withPropertyValues("spring.data.exposed.repositories.enabled=false")
            .run { context ->
                assertTrue(context.getBeansOfType(CustomerRepository::class.java).isEmpty())
            }
    }

    @Test
    fun `configuration backs off without a data source`() {
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ExposedRepositoriesAutoConfiguration::class.java))
            .run { context ->
                assertTrue(context.getBeansOfType(ExposedRepositoriesAutoConfiguration::class.java).isEmpty())
                assertTrue(context.getBeansOfType(CustomerRepository::class.java).isEmpty())
            }
    }

    @Test
    fun `user enabled repositories configuration takes precedence`() {
        testRunner
            .withUserConfiguration(ManuallyEnabledRepositories::class.java)
            .run { context ->
                assertTrue(context.getBeansOfType(ExposedRepositoriesAutoConfiguration::class.java).isEmpty())
                assertEquals(1, context.getBeansOfType(CustomerRepository::class.java).size)
                assertTrue(context.getBean("transactionManager") is SpringTransactionManager)
            }
    }

    private val testRunner: ApplicationContextRunner
        get() = ApplicationContextRunner()
            .withInitializer(
                ApplicationContextInitializer<ConfigurableApplicationContext> { context ->
                    AutoConfigurationPackages.register(context as GenericApplicationContext, StarterApplication::class.java.packageName)
                }
            )
            .withConfiguration(
                AutoConfigurations.of(
                    ExposedAutoConfiguration::class.java,
                    ExposedRepositoriesAutoConfiguration::class.java,
                    ExposedTransactionManagerAutoConfiguration::class.java
                )
            )
            .withBean(
                DataSource::class.java,
                Supplier {
                    EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true).build()
                }
            )

    @TestConfiguration
    @EnableExposedRepositories(
        basePackageClasses = [CustomerRepository::class],
        transactionManagerRef = "springTransactionManager"
    )
    open class ManuallyEnabledRepositories
}
