package org.jetbrains.exposed.v1.spring.data.autoconfigure

import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.spring.data.EnableExposedRepositories
import org.jetbrains.exposed.v1.spring.data.ExposedRepositoriesConfig
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryConfigExtension
import org.springframework.boot.autoconfigure.data.AbstractRepositoryConfigurationSourceSupport
import org.springframework.core.env.Environment
import org.springframework.data.repository.config.BootstrapMode
import org.springframework.data.repository.config.RepositoryConfigurationExtension

/**
 * Registers Exposed repositories found in the auto-configuration packages, as if
 * [EnableExposedRepositories] was declared on the Spring Boot application class.
 */
@OptIn(InternalApi::class)
internal class ExposedRepositoriesRegistrar : AbstractRepositoryConfigurationSourceSupport() {
    private var bootstrapMode: BootstrapMode = BootstrapMode.DEFAULT

    override fun getAnnotation(): Class<out Annotation> = ExposedRepositoriesConfig::class.java

    override fun getConfiguration(): Class<*> = EnableExposedRepositoriesConfiguration::class.java

    override fun getRepositoryConfigurationExtension(): RepositoryConfigurationExtension = ExposedRepositoryConfigExtension()

    override fun getBootstrapMode(): BootstrapMode = bootstrapMode

    override fun setEnvironment(environment: Environment) {
        super.setEnvironment(environment)
        environment.getProperty(BOOTSTRAP_MODE_PROPERTY)?.let { mode ->
            bootstrapMode = BootstrapMode.valueOf(mode.uppercase())
        }
    }

    companion object {
        const val BOOTSTRAP_MODE_PROPERTY = "spring.data.exposed.repositories.bootstrap-mode"
    }
}
