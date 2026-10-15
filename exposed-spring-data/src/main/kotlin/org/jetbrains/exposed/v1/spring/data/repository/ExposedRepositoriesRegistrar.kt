package org.jetbrains.exposed.v1.spring.data.repository

import org.jetbrains.exposed.v1.spring.data.EnableExposedRepositories
import org.springframework.data.repository.config.RepositoryBeanDefinitionRegistrarSupport
import org.springframework.data.repository.config.RepositoryConfigurationExtension

class ExposedRepositoriesRegistrar : RepositoryBeanDefinitionRegistrarSupport() {
    override fun getAnnotation(): Class<out Annotation> {
        return EnableExposedRepositories::class.java
    }

    override fun getExtension(): RepositoryConfigurationExtension {
        return ExposedRepositoryConfigExtension()
    }
}
