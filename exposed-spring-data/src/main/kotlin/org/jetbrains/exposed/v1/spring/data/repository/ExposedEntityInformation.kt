package org.jetbrains.exposed.v1.spring.data.repository

import org.springframework.data.annotation.Id
import org.springframework.data.repository.core.RepositoryMetadata
import org.springframework.data.repository.core.support.AbstractEntityInformation
import java.lang.reflect.Method
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter

@Suppress("UNCHECKED_CAST")
class ExposedEntityInformation<T : Any, ID : Any>(
    metadata: RepositoryMetadata,
) : AbstractEntityInformation<T, ID>(metadata.domainType as Class<T>) {
    private val domainTypeName: String = metadata.domainTypeName
    private val idType = metadata.idType as Class<ID>
    private val idGetter: Method = metadata.domainType.kotlin.idProperty.javaGetter
        ?: error("ID property of $domainTypeName has no getter")

    @Suppress("MaximumLineLength")
    private val KClass<*>.idProperty: KProperty<*>
        get() {
            val idProperty = memberProperties.singleOrNull { it.hasIdAnnotation() } ?: memberProperties.singleOrNull { it.isId() }
            return checkNotNull(idProperty) {
                "Entity class $domainTypeName does not have an ID property. Define either a property with name 'id' or annotate a property with @Id annotation."
            }
        }

    private fun KProperty<*>.isId() = name == "id"

    private fun KProperty<*>.hasIdAnnotation() =
        javaField?.isAnnotationPresent(Id::class.java) == true ||
            javaGetter?.isAnnotationPresent(Id::class.java) == true

    override fun getId(entity: T): ID? = idGetter.invoke(entity) as ID?

    override fun getIdType(): Class<ID> = idType
}
