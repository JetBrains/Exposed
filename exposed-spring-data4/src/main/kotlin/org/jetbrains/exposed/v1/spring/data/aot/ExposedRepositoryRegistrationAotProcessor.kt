package org.jetbrains.exposed.v1.spring.data.aot

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.spring.data.ExposedRepository
import org.springframework.aop.SpringProxy
import org.springframework.aot.generate.GenerationContext
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeHint
import org.springframework.core.DecoratingProxy
import org.springframework.data.projection.TargetAware
import org.springframework.data.repository.config.AotRepositoryContext
import org.springframework.data.repository.config.RepositoryRegistrationAotProcessor
import org.springframework.data.repository.core.RepositoryInformation
import java.lang.reflect.Method
import java.util.function.Consumer

/**
 * Registers the runtime hints an Exposed repository needs in a native image, in addition to the ones of
 * [RepositoryRegistrationAotProcessor]:
 *
 * - the table `object` of [ExposedRepository]: its `INSTANCE` field and the properties of its columns, read with kotlin-reflect;
 *   like for the domain type and the projections, also every type kotlin-reflect resolves by name, see
 *   [registerTypesResolvedByKotlinReflect];
 * - the domain type: constructors, fields and methods, used to create, read and write entities;
 * - every static projection returned by a method of the repository, also by `query { }` default methods, after unwrapping
 *   `List`, `Optional`, `Page`, `Slice`, `Window`, `Stream` etc.: a DTO gets constructors, fields and methods, an interface
 *   gets its methods and the JDK proxy that the projection factory creates for it.
 *
 * Projection types that are only known when the method is called (a `Class<T>` argument, `query<T> { }` in a generic method)
 * can't be found here and need `@RegisterReflectionForBinding`.
 */
internal class ExposedRepositoryRegistrationAotProcessor : RepositoryRegistrationAotProcessor() {
    override fun configureTypeContributions(repositoryContext: AotRepositoryContext, generationContext: GenerationContext) {
        super.configureTypeContributions(repositoryContext, generationContext)

        val information = repositoryContext.repositoryInformation
        val hints = generationContext.runtimeHints

        // the table hierarchy, because kotlin-reflect also reads the inherited properties (e.g. `id`) and their getters
        information.repositoryInterface.getAnnotation(ExposedRepository::class.java)?.let { registerClassHierarchy(it.table.java, hints) }
        registerClassHierarchy(information.domainType, hints)
        projectionTypes(information).forEach { registerProjection(it, hints) }
    }

    private fun registerProjection(type: Class<*>, hints: RuntimeHints) {
        if (type.isInterface) {
            registerInterfaceHierarchy(type, hints)
            // the interfaces ProxyProjectionFactory gives the opaque projection proxy, see AotTypeConfiguration.usedAsProjectionInterface()
            hints.proxies().registerJdkProxy(type, TargetAware::class.java, SpringProxy::class.java, DecoratingProxy::class.java)
        } else {
            registerClassHierarchy(type, hints)
        }
    }

    private fun projectionTypes(information: RepositoryInformation): Set<Class<*>> =
        information.repositoryInterface.methods
            .asSequence()
            .filterNot { it.isBridge || it.isSynthetic }
            .mapNotNull { returnedType(information, it) }
            .filter { isProjectionCandidate(it, information.domainType) }
            .toCollection(LinkedHashSet())

    // methods whose return type can't be resolved (e.g. raw wrappers) have no static projection
    private fun returnedType(information: RepositoryInformation, method: Method): Class<*>? =
        runCatching { information.getReturnedDomainClass(method) }.getOrNull()

    private fun isProjectionCandidate(type: Class<*>, domainType: Class<*>): Boolean =
        !type.isPrimitive &&
            !type.isArray &&
            !type.isEnum &&
            !type.isAnnotation &&
            type != ResultRow::class.java &&
            !type.isAssignableFrom(domainType) &&
            !isPlatformType(type)

    private fun registerClassHierarchy(type: Class<*>, hints: RuntimeHints) {
        generateSequence(type) { it.superclass }
            .takeWhile { !isPlatformType(it) }
            .forEach { hints.reflection().registerType(it, CLASS_MEMBERS) }
        hints.registerTypesResolvedByKotlinReflect(type)
    }

    private fun registerInterfaceHierarchy(type: Class<*>, hints: RuntimeHints) {
        registerInterfaces(type, hints)
        hints.registerTypesResolvedByKotlinReflect(type)
    }

    private fun registerInterfaces(type: Class<*>, hints: RuntimeHints) {
        if (isPlatformType(type)) return
        hints.reflection().registerType(type, INTERFACE_MEMBERS)
        type.interfaces.forEach { registerInterfaces(it, hints) }
    }

    private companion object {
        val CLASS_MEMBERS = Consumer<TypeHint.Builder> {
            it.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS)
        }

        val INTERFACE_MEMBERS = Consumer<TypeHint.Builder> {
            it.withMembers(MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_PUBLIC_METHODS)
        }

        private val PLATFORM_PACKAGES = listOf("java.", "javax.", "jdk.", "kotlin.", "org.springframework.")

        fun isPlatformType(type: Class<*>): Boolean = PLATFORM_PACKAGES.any { type.name.startsWith(it) }
    }
}
