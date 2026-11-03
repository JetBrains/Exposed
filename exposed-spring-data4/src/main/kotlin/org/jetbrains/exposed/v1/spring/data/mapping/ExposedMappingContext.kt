package org.jetbrains.exposed.v1.spring.data.mapping

import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.data.annotation.Id
import org.springframework.data.core.TypeInformation
import org.springframework.data.mapping.Association
import org.springframework.data.mapping.PersistentEntity
import org.springframework.data.mapping.context.AbstractMappingContext
import org.springframework.data.mapping.model.AnnotationBasedPersistentProperty
import org.springframework.data.mapping.model.BasicPersistentEntity
import org.springframework.data.mapping.model.EntityInstantiators
import org.springframework.data.mapping.model.Property
import org.springframework.data.mapping.model.SimpleTypeHolder
import org.springframework.util.ReflectionUtils
import java.util.concurrent.ConcurrentHashMap

internal typealias ExposedPersistentEntity<T> = BasicPersistentEntity<T, ExposedPersistentProperty>

internal class ExposedMappingContext : AbstractMappingContext<ExposedPersistentEntity<*>, ExposedPersistentProperty>() {
    val instantiators: EntityInstantiators = EntityInstantiators()

    override fun <T : Any> createPersistentEntity(typeInformation: TypeInformation<T>): ExposedPersistentEntity<*> =
        ExposedPersistentEntity(typeInformation)

    override fun createPersistentProperty(
        property: Property,
        owner: ExposedPersistentEntity<*>,
        simpleTypeHolder: SimpleTypeHolder,
    ): ExposedPersistentProperty =
        ExposedPersistentProperty(property, owner, simpleTypeHolder)
}

internal class ExposedPersistentProperty(
    property: Property,
    owner: PersistentEntity<*, ExposedPersistentProperty>,
    simpleTypeHolder: SimpleTypeHolder,
) : AnnotationBasedPersistentProperty<ExposedPersistentProperty>(property, owner, simpleTypeHolder) {
    override fun isIdProperty(): Boolean = super.isIdProperty() || (name == ID_PROPERTY && !hasExplicitId(owner.type))

    override fun isEntity(): Boolean = false

    override fun getPersistentEntityTypeInformation(): Iterable<TypeInformation<*>> = emptyList()

    override fun createAssociation(): Association<ExposedPersistentProperty> =
        throw UnsupportedOperationException("Associations are not supported (property '$name' of ${owner.type.name})")
}

private const val ID_PROPERTY = "id"

private val explicitIds = ConcurrentHashMap<Class<*>, Boolean>()

private fun hasExplicitId(type: Class<*>): Boolean = explicitIds.computeIfAbsent(type) {
    var found = false
    ReflectionUtils.doWithFields(type) { if (AnnotatedElementUtils.hasAnnotation(it, Id::class.java)) found = true }
    ReflectionUtils.doWithMethods(type) { if (AnnotatedElementUtils.hasAnnotation(it, Id::class.java)) found = true }
    found
}
