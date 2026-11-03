package org.jetbrains.exposed.v1.spring.data.aot

import org.springframework.aot.hint.RuntimeHints
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType

/**
 * Registers [type], all of its supertypes and every type that appears in the signatures of their declared fields, methods and
 * constructors (type arguments included), without members.
 *
 * kotlin-reflect resolves the supertypes and member types of a class by name while it computes the members of the class
 * (`KClass.members`, `memberProperties`), and in a native image a class can only be found by name if it is registered. Without
 * these hints, reading the properties of e.g. a table `object` or of a Kotlin bean whose Java superclass has a field of an
 * unregistered type fails with `UnsupportedOperationException: Type not found`. Spring Data reads the bean properties of Kotlin
 * classes with kotlin-reflect too (`KotlinBeanInfoFactory`), so this also applies to the infrastructure beans of this module.
 *
 * The members of JDK and Kotlin classes in the hierarchy are not walked.
 */
internal fun RuntimeHints.registerTypesResolvedByKotlinReflect(type: Class<*>) {
    val collector = SignatureTypeCollector()
    collector.walkHierarchy(type)
    collector.types.forEach { reflection().registerType(it) }
}

private class SignatureTypeCollector {
    val types = LinkedHashSet<Class<*>>()
    private val walked = HashSet<Class<*>>()
    private val visitedVariables = HashSet<TypeVariable<*>>()

    fun walkHierarchy(type: Class<*>) {
        if (type.isPrimitive || type.isArray || !walked.add(type)) return
        types += type
        if (isJdkOrKotlinType(type)) return

        // a member signature may reference an optional class that isn't on the class path; that class can't be loaded at runtime either
        runCatching {
            type.declaredFields.forEach { add(it.genericType) }
            type.declaredMethods.forEach { method ->
                add(method.genericReturnType)
                method.genericParameterTypes.forEach(::add)
            }
            type.declaredConstructors.forEach { constructor -> constructor.genericParameterTypes.forEach(::add) }
        }
        runCatching {
            type.genericSuperclass?.let(::add)
            type.genericInterfaces.forEach(::add)
        }
        type.superclass?.let(::walkHierarchy)
        type.interfaces.forEach(::walkHierarchy)
    }

    private fun add(type: Type) {
        when (type) {
            is Class<*> -> when {
                type.isArray -> add(type.componentType)
                !type.isPrimitive -> types += type
            }
            is ParameterizedType -> {
                add(type.rawType)
                type.ownerType?.let(::add)
                type.actualTypeArguments.forEach(::add)
            }
            is GenericArrayType -> add(type.genericComponentType)
            is WildcardType -> {
                type.upperBounds.forEach(::add)
                type.lowerBounds.forEach(::add)
            }
            is TypeVariable<*> -> if (visitedVariables.add(type)) type.bounds.forEach(::add)
        }
    }

    private fun isJdkOrKotlinType(type: Class<*>): Boolean = JDK_AND_KOTLIN_PACKAGES.any { type.name.startsWith(it) }

    private companion object {
        val JDK_AND_KOTLIN_PACKAGES = listOf("java.", "javax.", "jdk.", "sun.", "kotlin.")
    }
}
