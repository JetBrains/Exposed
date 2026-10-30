package org.jetbrains.exposed.v1.spring.data.repository

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.spring.data.ExposedRepositoriesConfig
import org.springframework.data.repository.config.RepositoryBeanDefinitionRegistrarSupport
import org.springframework.data.repository.config.RepositoryConfigurationExtension

@OptIn(InternalApi::class)
internal class ExposedRepositoriesRegistrar : RepositoryBeanDefinitionRegistrarSupport() {
    override fun getAnnotation(): Class<out Annotation> {
        return ExposedRepositoriesConfig::class.java
    }

    override fun getExtension(): RepositoryConfigurationExtension {
        return ExposedRepositoryConfigExtension()
    }
}
