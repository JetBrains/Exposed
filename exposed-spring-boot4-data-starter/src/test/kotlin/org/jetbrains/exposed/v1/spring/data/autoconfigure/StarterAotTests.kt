package org.jetbrains.exposed.v1.spring.data.autoconfigure

import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.aot.AotDetector
import org.springframework.aot.generate.GeneratedFiles
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import org.springframework.aot.test.generate.TestGenerationContext
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.SpringApplicationHook
import org.springframework.boot.SpringApplicationRunListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.ConfigurationClassPostProcessor
import org.springframework.context.aot.ApplicationContextAotGenerator
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.SpringProperties
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.test.tools.CompileWithForkedClassLoader
import org.springframework.core.test.tools.TestCompiler
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@CompileWithForkedClassLoader
class StarterAotTests {
    private lateinit var context: GenericApplicationContext
    private lateinit var generationContext: TestGenerationContext
    private lateinit var repository: CustomerRepository
    private var aotDataSourceSingletons: List<String> = emptyList()
    private var generatedSources: Map<String, String> = emptyMap()
    private var aotModeDuringRefresh = false

    @BeforeAll
    fun startGeneratedContext() {
        generationContext = TestGenerationContext(StarterApplication::class.java)
        val previousAotProcessing = SpringProperties.getProperty(AOT_PROCESSING)
        SpringProperties.setProperty(AOT_PROCESSING, "true")
        val initializerClassName = try {
            generateApplicationContext()
        } finally {
            SpringProperties.setProperty(AOT_PROCESSING, previousAotProcessing)
        }

        generationContext.writeGeneratedContent()
        check(initializerClassName.reflectionName() == "${StarterApplication::class.java.name}__ApplicationContextInitializer")
        generatedSources = generationContext.generatedFiles.getGeneratedFiles(GeneratedFiles.Kind.SOURCE)
            .mapValues { (_, source) -> source.inputStream.use { String(it.readAllBytes()) } }

        val previousAotEnabled = SpringProperties.getProperty(AOT_ENABLED)
        SpringProperties.setProperty(AOT_ENABLED, "true")
        try {
            TestCompiler.forSystem().with(generationContext).compile { compiled ->
                val thread = Thread.currentThread()
                val previousClassLoader = thread.contextClassLoader
                try {
                    thread.contextClassLoader = compiled.classLoader
                    val application = SpringApplication(StarterApplication::class.java)
                    application.setMainApplicationClass(StarterApplication::class.java)
                    application.setResourceLoader(DefaultResourceLoader(compiled.classLoader))
                    context = application.run(
                        "--spring.datasource.url=jdbc:h2:mem:exposed_spring_data_starter_aot;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.exposed.generate-ddl=true",
                        "--spring.main.web-application-type=none"
                    ) as GenericApplicationContext
                    aotModeDuringRefresh = AotDetector.useGeneratedArtifacts()
                } finally {
                    thread.contextClassLoader = previousClassLoader
                }
            }
        } finally {
            SpringProperties.setProperty(AOT_ENABLED, previousAotEnabled)
        }

        repository = context.getBean(CustomerRepository::class.java)
    }

    @AfterAll
    fun closeGeneratedContext() {
        if (::context.isInitialized) context.close()
    }

    @Test
    fun `AOT processing does not create the data source`() {
        assertTrue(aotDataSourceSingletons.isEmpty(), "data source singletons created during AOT processing")
    }

    @Test
    fun `starter auto configuration is included in generated code`() {
        val generatedCode = generatedSources.values.joinToString("\n")

        assertTrue("ExposedRepositoryFactoryBean" in generatedCode, "generated repository bean definitions")
        assertTrue("exposedMappingContext" in generatedCode, "generated mapping context bean definition")
        assertTrue(context.containsBean("exposedMappingContext"))
        assertTrue(context.getBeanNamesForType(ConfigurationClassPostProcessor::class.java).isEmpty())
        assertTrue(aotModeDuringRefresh, "AOT mode enabled while the generated context was refreshed")
    }

    @Test
    fun `repository registration contributes table and entity runtime hints`() {
        assertTrue(
            RuntimeHintsPredicates.reflection()
                .onType(Customer::class.java)
                .withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS)
                .test(generationContext.runtimeHints)
        )
        assertTrue(
            RuntimeHintsPredicates.reflection()
                .onType(CustomersTable::class.java)
                .withMemberCategory(MemberCategory.ACCESS_DECLARED_FIELDS)
                .test(generationContext.runtimeHints)
        )
    }

    @Test
    fun `generated application supports repository operations and starter transaction manager`() {
        val saved = repository.save(Customer(name = "Ada"))
        val id = checkNotNull(saved.id)

        assertEquals(saved, repository.findByIdOrNull(id))
        assertEquals(saved, repository.findByName("Ada"))
        assertSame(
            context.getBean("springTransactionManager", SpringTransactionManager::class.java),
            context.getBean("transactionManager", PlatformTransactionManager::class.java)
        )
    }

    private fun generateApplicationContext() = runApplicationToContext().use { aotContext ->
        val initializerClassName = ApplicationContextAotGenerator()
            .processAheadOfTime(aotContext, generationContext)
        aotDataSourceSingletons = aotContext.beanFactory.dataSourceSingletons()
        initializerClassName
    }

    private fun runApplicationToContext(): GenericApplicationContext {
        var aotContext: GenericApplicationContext? = null
        val hook = SpringApplicationHook { _ ->
            object : SpringApplicationRunListener {
                override fun contextLoaded(context: ConfigurableApplicationContext) {
                    aotContext = context as? GenericApplicationContext
                    throw SpringApplication.AbandonedRunException(context)
                }
            }
        }

        try {
            SpringApplication.withHook(hook) {
                SpringApplication(StarterApplication::class.java).run(
                    "--spring.datasource.url=jdbc:h2:mem:exposed_spring_data_starter_aot;DB_CLOSE_DELAY=-1",
                    "--spring.datasource.driver-class-name=org.h2.Driver",
                    "--spring.exposed.generate-ddl=true",
                    "--spring.main.web-application-type=none"
                )
            }
        } catch (_: SpringApplication.AbandonedRunException) {
            return requireNotNull(aotContext) { "Spring Boot did not provide an AOT application context" }
        }

        error("Spring Boot application startup was not abandoned after context loading")
    }

    private fun ConfigurableListableBeanFactory.dataSourceSingletons(): List<String> = singletonNames
        .filter { getSingleton(it) is DataSource }

    private companion object {
        const val AOT_ENABLED = "spring.aot.enabled"
        const val AOT_PROCESSING = "spring.aot.processing"
    }
}
