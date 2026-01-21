package org.jetbrains.exposed.v1.iface.ksp.generators

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.exposed.v1.iface.ksp.model.EntityInfo

/**
 * Generates entity implementation class from interface.
 *
 * Example output:
 * ```kotlin
 * class UserEntity(id: EntityID<Int>) : IntEntity(id), User {
 *     companion object : IntEntityClass<UserEntity>(UserTable)
 *
 *     override var name by UserTable.name
 *     override var email by UserTable.email
 * }
 * ```
 */
class EntityImplGenerator(private val entityInfo: EntityInfo) {

    fun generate(): FileSpec {
        val interfaceType = ClassName(entityInfo.packageName, entityInfo.interfaceName)
        val tableType = ClassName(entityInfo.packageName, entityInfo.tableClassName)
        val entityType = ClassName(entityInfo.packageName, entityInfo.entityClassName)
        val entityIdType = ClassName("org.jetbrains.exposed.v1.core.dao.id", "EntityID")

        val idTypeParam = getIdTypeParam()

        val entityClass = TypeSpec.classBuilder(entityInfo.entityClassName)
            // Make public so users can call EntityName.new { }
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("id", entityIdType.parameterizedBy(idTypeParam))
                    .build()
            )
            .superclass(getEntitySuperclass())
            .addSuperclassConstructorParameter("id")
            .addSuperinterface(interfaceType)
            .addType(generateCompanionObject(entityType, tableType))
            .apply {
                // Add property delegations
                entityInfo.properties
                    .filterNot { it.isPrimaryKey }
                    .forEach { property ->
                        addProperty(generatePropertyDelegation(property, tableType))
                    }
            }
            .build()

        return FileSpec.builder(entityInfo.packageName, entityInfo.entityClassName)
            .addType(entityClass)
            .addImport("org.jetbrains.exposed.v1.core.dao.id", "EntityID")
            .addImport("org.jetbrains.exposed.v1.dao", getEntityClassName())
            .addImport("org.jetbrains.exposed.v1.dao", getEntityClassClassName())
            .build()
    }

    private fun getIdTypeParam(): TypeName {
        return when (entityInfo.idProperty.typeName.toString().removeSuffix("?")) {
            "kotlin.Int", "Int" -> INT
            "kotlin.Long", "Long" -> LONG
            else -> INT // Default to Int
        }
    }

    private fun getEntitySuperclass(): ClassName {
        return when (entityInfo.idProperty.typeName.toString().removeSuffix("?")) {
            "kotlin.Int", "Int" -> ClassName("org.jetbrains.exposed.v1.dao", "IntEntity")
            "kotlin.Long", "Long" -> ClassName("org.jetbrains.exposed.v1.dao", "LongEntity")
            else -> ClassName("org.jetbrains.exposed.v1.dao", "IntEntity") // Default to Int
        }
    }

    private fun getEntityClassName(): String {
        return when (entityInfo.idProperty.typeName.toString().removeSuffix("?")) {
            "kotlin.Int", "Int" -> "IntEntity"
            "kotlin.Long", "Long" -> "LongEntity"
            else -> "IntEntity"
        }
    }

    private fun getEntityClassClassName(): String {
        return when (entityInfo.idProperty.typeName.toString().removeSuffix("?")) {
            "kotlin.Int", "Int" -> "IntEntityClass"
            "kotlin.Long", "Long" -> "LongEntityClass"
            else -> "IntEntityClass"
        }
    }

    private fun generateCompanionObject(entityType: TypeName, tableType: TypeName): TypeSpec {
        val entityClassType = when (entityInfo.idProperty.typeName.toString().removeSuffix("?")) {
            "kotlin.Int", "Int" -> ClassName("org.jetbrains.exposed.v1.dao", "IntEntityClass")
            "kotlin.Long", "Long" -> ClassName("org.jetbrains.exposed.v1.dao", "LongEntityClass")
            else -> ClassName("org.jetbrains.exposed.v1.dao", "IntEntityClass")
        }

        return TypeSpec.companionObjectBuilder()
            .superclass(entityClassType.parameterizedBy(entityType))
            .addSuperclassConstructorParameter("%T", tableType)
            .build()
    }

    private fun generatePropertyDelegation(
        property: org.jetbrains.exposed.v1.iface.ksp.model.PropertyInfo,
        tableType: ClassName
    ): PropertySpec {
        return PropertySpec.builder(property.name, property.typeName, KModifier.OVERRIDE)
            .mutable(property.isMutable)
            .delegate("%T.%N", tableType, property.name)
            .build()
    }
}
