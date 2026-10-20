package org.jetbrains.exposed.v1.spring.data.repository

import org.jetbrains.exposed.v1.spring.data.ExposedCrudRepository
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedMappingContext
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.data.repository.config.RepositoryConfigurationExtensionSupport
import org.springframework.data.repository.config.RepositoryConfigurationSource

/**
 * [org.springframework.data.repository.config.RepositoryConfigurationExtension] for Exposed repositories.
 *
 * Used by [org.jetbrains.exposed.v1.spring.data.EnableExposedRepositories] and by the Spring Boot auto-configuration
 * of the `exposed-spring-data-starter` module.
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

    override fun getIdentifyingTypes(): Collection<Class<*>> {
        return listOf(ExposedCrudRepository::class.java)
    }

    /** Registers one mapping context for all Exposed repositories of the application context, unless there already is one. */
    override fun registerBeansForRoot(registry: BeanDefinitionRegistry, configurationSource: RepositoryConfigurationSource) {
        super.registerBeansForRoot(registry, configurationSource)
        if (!registry.containsBeanDefinition(MAPPING_CONTEXT_BEAN_NAME)) {
            registry.registerBeanDefinition(MAPPING_CONTEXT_BEAN_NAME, RootBeanDefinition(ExposedMappingContext::class.java))
        }
    }

    companion object {
        const val MAPPING_CONTEXT_BEAN_NAME = "exposedMappingContext"
    }
}
