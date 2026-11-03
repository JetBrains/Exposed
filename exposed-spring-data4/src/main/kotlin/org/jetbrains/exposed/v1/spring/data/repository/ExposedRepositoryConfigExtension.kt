package org.jetbrains.exposed.v1.spring.data.repository

import org.jetbrains.exposed.v1.spring.data.ExposedCrudRepository
import org.jetbrains.exposed.v1.spring.data.aot.ExposedRepositoryRegistrationAotProcessor
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedMappingContext
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor
import org.springframework.beans.factory.support.BeanDefinitionBuilder
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.data.repository.config.AnnotationRepositoryConfigurationSource
import org.springframework.data.repository.config.RepositoryConfigurationExtensionSupport
import org.springframework.data.repository.config.RepositoryConfigurationSource

/**
 * [org.springframework.data.repository.config.RepositoryConfigurationExtension] for Exposed repositories.
 *
 * Used by [org.jetbrains.exposed.v1.spring.data.EnableExposedRepositories] and by the Spring Boot auto-configuration
 * of the `exposed-spring-boot4-data-starter` module.
 */
class ExposedRepositoryConfigExtension : RepositoryConfigurationExtensionSupport() {
    @Deprecated("Deprecated in Java")
    override fun getModulePrefix(): String {
        return "exposed"
    }

    override fun getModuleIdentifier(): String {
        return "exposed"
    }

    override fun getModuleName(): String {
        return moduleIdentifier.replaceFirstChar { it.titlecase() }
    }

    override fun getRepositoryFactoryBeanClassName(): String {
        return checkNotNull(ExposedRepositoryFactoryBean::class.qualifiedName) {
            "Unable to find qualified name for ExposedRepositoryFactoryBean"
        }
    }

    override fun postProcess(builder: BeanDefinitionBuilder, config: AnnotationRepositoryConfigurationSource) {
        config.getAttribute("transactionManagerRef", String::class.java).ifPresent { transactionManagerRef ->
            builder.addPropertyValue("transactionManager", transactionManagerRef)
        }
    }

    override fun getIdentifyingTypes(): Collection<Class<*>> {
        return listOf(ExposedCrudRepository::class.java)
    }

    /** Registers the runtime hints for the table, entity and projections of every repository when processing ahead of time. */
    override fun getRepositoryAotProcessor(): Class<out BeanRegistrationAotProcessor> =
        ExposedRepositoryRegistrationAotProcessor::class.java

    /**
     * Registers one mapping context and one table registry for all Exposed repositories of the application context, unless there already are.
     *
     * This runs once per configuration source (each `@EnableExposedRepositories`). The table registry holds the table objects
     * found in the base packages of all sources: a later source adds its packages to the registry registered by an earlier one.
     * A table registry bean defined by the application is kept as it is.
     */
    override fun registerBeansForRoot(registry: BeanDefinitionRegistry, configurationSource: RepositoryConfigurationSource) {
        super.registerBeansForRoot(registry, configurationSource)
        if (!registry.containsBeanDefinition(MAPPING_CONTEXT_BEAN_NAME)) {
            registry.registerBeanDefinition(MAPPING_CONTEXT_BEAN_NAME, RootBeanDefinition(ExposedMappingContext::class.java))
        }
        registerTableRegistry(registry, configurationSource.basePackages.toList())
    }

    private fun registerTableRegistry(registry: BeanDefinitionRegistry, basePackages: List<String>) {
        if (!registry.containsBeanDefinition(TABLE_REGISTRY_BEAN_NAME)) {
            val definition = RootBeanDefinition(ExposedTableRegistryFactoryBean::class.java)
            definition.constructorArgumentValues.addIndexedArgumentValue(0, basePackages.distinct())
            registry.registerBeanDefinition(TABLE_REGISTRY_BEAN_NAME, definition)
            return
        }
        val existing = registry.getBeanDefinition(TABLE_REGISTRY_BEAN_NAME)
        if (existing.beanClassName != ExposedTableRegistryFactoryBean::class.java.name) return
        val arguments = existing.constructorArgumentValues
        val registered = arguments.getIndexedArgumentValue(0, List::class.java)?.value as? List<*> ?: emptyList<Any>()
        arguments.addIndexedArgumentValue(0, (registered.filterIsInstance<String>() + basePackages).distinct())
    }

    companion object {
        const val MAPPING_CONTEXT_BEAN_NAME = "exposedMappingContext"
        const val TABLE_REGISTRY_BEAN_NAME = "exposedTableRegistry"
    }
}
