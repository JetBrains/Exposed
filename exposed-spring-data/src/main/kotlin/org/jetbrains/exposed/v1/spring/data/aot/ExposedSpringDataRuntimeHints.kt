package org.jetbrains.exposed.v1.spring.data.aot

import org.jetbrains.exposed.v1.spring.data.EnableExposedRepositories
import org.jetbrains.exposed.v1.spring.data.ExposedCrudRepository
import org.jetbrains.exposed.v1.spring.data.ExposedRepository
import org.jetbrains.exposed.v1.spring.data.auditing.EnableExposedAuditing
import org.jetbrains.exposed.v1.spring.data.auditing.ExposedAuditingCallback
import org.jetbrains.exposed.v1.spring.data.auditing.ExposedAuditingRegistrar
import org.jetbrains.exposed.v1.spring.data.event.AfterConvertCallback
import org.jetbrains.exposed.v1.spring.data.event.AfterDeleteCallback
import org.jetbrains.exposed.v1.spring.data.event.AfterSaveCallback
import org.jetbrains.exposed.v1.spring.data.event.BeforeConvertCallback
import org.jetbrains.exposed.v1.spring.data.event.BeforeDeleteCallback
import org.jetbrains.exposed.v1.spring.data.event.BeforeSaveCallback
import org.jetbrains.exposed.v1.spring.data.mapping.ExposedMappingContext
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoriesRegistrar
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryConfigExtension
import org.jetbrains.exposed.v1.spring.data.repository.ExposedRepositoryFactoryBean
import org.jetbrains.exposed.v1.spring.data.repository.SimpleExposedRepository
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

/**
 * Runtime hints for the fixed infrastructure of Exposed Spring Data, registered through `META-INF/spring/aot.factories`.
 *
 * The hints for the tables, entities and projections of the repositories of an application are registered per repository by
 * [ExposedRepositoryRegistrationAotProcessor].
 */
internal class ExposedSpringDataRuntimeHints : RuntimeHintsRegistrar {
    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val reflection = hints.reflection()

        // beans that are created from bean definitions with a class (name) and configured through setters / Aware callbacks
        val beans = listOf(
            ExposedRepositoryFactoryBean::class.java,
            ExposedMappingContext::class.java,
            ExposedAuditingCallback::class.java,
        )
        val configuration = listOf(
            ExposedRepositoriesRegistrar::class.java,
            ExposedAuditingRegistrar::class.java,
            ExposedRepositoryConfigExtension::class.java,
            ExposedRepositoryRegistrationAotProcessor::class.java,
        )
        (beans + configuration).forEach {
            reflection.registerType(it, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS)
        }
        // Spring Data reads the bean properties of Kotlin classes with kotlin-reflect (KotlinBeanInfoFactory) when it applies the
        // property values of their bean definitions, which resolves all types of their hierarchy by name
        beans.forEach { hints.registerTypesResolvedByKotlinReflect(it) }

        // repository methods are invoked reflectively on the base implementation
        reflection.registerType(SimpleExposedRepository::class.java, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_PUBLIC_METHODS)
        reflection.registerType(ExposedCrudRepository::class.java, MemberCategory.INVOKE_PUBLIC_METHODS)

        // EntityCallbacks looks up the callback method on the callback interface and invokes it reflectively
        listOf(
            BeforeConvertCallback::class.java,
            BeforeSaveCallback::class.java,
            AfterSaveCallback::class.java,
            AfterConvertCallback::class.java,
            BeforeDeleteCallback::class.java,
            AfterDeleteCallback::class.java,
        ).forEach { reflection.registerType(it, MemberCategory.INVOKE_DECLARED_METHODS) }

        // annotations read at runtime, e.g. the table of @ExposedRepository
        listOf(ExposedRepository::class.java, EnableExposedRepositories::class.java, EnableExposedAuditing::class.java)
            .forEach { reflection.registerType(it, MemberCategory.INVOKE_PUBLIC_METHODS) }

        // kotlin-reflect, used to read table columns, entity properties and DTO constructors
        reflection.registerType(Metadata::class.java, MemberCategory.INVOKE_PUBLIC_METHODS)
        hints.resources().registerPattern("kotlin/**/*.kotlin_builtins")
        hints.resources().registerPattern("META-INF/*.kotlin_module")
    }
}
