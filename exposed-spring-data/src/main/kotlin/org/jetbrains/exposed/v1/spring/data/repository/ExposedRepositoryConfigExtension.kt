package org.jetbrains.exposed.v1.spring.data.repository

import org.jetbrains.exposed.v1.spring.data.ExposedCrudRepository
import org.springframework.data.repository.config.RepositoryConfigurationExtensionSupport

internal class ExposedRepositoryConfigExtension : RepositoryConfigurationExtensionSupport() {
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
}
