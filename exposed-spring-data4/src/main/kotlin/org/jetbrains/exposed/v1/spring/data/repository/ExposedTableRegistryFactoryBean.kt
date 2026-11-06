package org.jetbrains.exposed.v1.spring.data.repository

import org.jetbrains.exposed.v1.spring.data.mapping.ExposedTableRegistry
import org.springframework.beans.factory.BeanClassLoaderAware
import org.springframework.beans.factory.FactoryBean

/** Creates the [ExposedTableRegistry] of an application context by scanning the [basePackages] of its Exposed repositories once. */
internal class ExposedTableRegistryFactoryBean(private val basePackages: List<String>) : FactoryBean<ExposedTableRegistry>, BeanClassLoaderAware {
    private var classLoader: ClassLoader? = null

    private val registry by lazy { ExposedTableRegistry.scan(basePackages, classLoader) }

    override fun setBeanClassLoader(classLoader: ClassLoader) {
        this.classLoader = classLoader
    }

    override fun getObject(): ExposedTableRegistry = registry

    override fun getObjectType(): Class<*> = ExposedTableRegistry::class.java
}
