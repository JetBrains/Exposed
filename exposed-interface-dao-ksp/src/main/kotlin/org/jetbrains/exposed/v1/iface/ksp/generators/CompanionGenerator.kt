package org.jetbrains.exposed.v1.iface.ksp.generators

import com.squareup.kotlinpoet.*
import org.jetbrains.exposed.v1.iface.ksp.model.EntityInfo

/**
 * Generates extension functions for the entity interface.
 *
 * Generated extensions provide convenient access to entity properties:
 * ```kotlin
 * val User.idValue: Int  // Access to ID value
 * fun User.toData(): UserData  // Convert to immutable data class
 * ```
 *
 * For all entity operations, use the Entity class directly:
 * ```kotlin
 * UserEntity.new { name = "Alice"; email = "alice@example.com" }
 * UserEntity.findById(id)
 * UserEntity.all()
 * ```
 */
class CompanionGenerator(private val entityInfo: EntityInfo) {

    fun generate(): FileSpec {
        val interfaceType = ClassName(entityInfo.packageName, entityInfo.interfaceName)
        val entityImplType = ClassName(entityInfo.packageName, entityInfo.entityClassName)
        val dataClassType = if (entityInfo.generateDataClass) {
            ClassName(entityInfo.packageName, entityInfo.dataClassName)
        } else {
            null
        }
        val idType = entityInfo.idProperty.typeName

        return FileSpec.builder(entityInfo.packageName, "${entityInfo.interfaceName}Extensions")
            .apply {
                // idValue extension property to access the EntityID value
                addProperty(
                    PropertySpec.builder("idValue", idType)
                        .receiver(interfaceType)
                        .getter(
                            FunSpec.getterBuilder()
                                .addCode("return (this as %T).id.value", entityImplType)
                                .build()
                        )
                        .build()
                )

                // toData() extension function if data class is generated
                if (dataClassType != null) {
                    addFunction(
                        FunSpec.builder("toData")
                            .receiver(interfaceType)
                            .returns(dataClassType)
                            .addCode(
                                CodeBlock.builder()
                                    .add("return %T(\n", dataClassType)
                                    .indent()
                                    .add("id = idValue,\n")
                                    .apply {
                                        entityInfo.properties.filterNot { it.isPrimaryKey }.forEachIndexed { index, property ->
                                            add("%N = %N", property.name, property.name)
                                            if (index < entityInfo.properties.filterNot { it.isPrimaryKey }.size - 1) {
                                                add(",\n")
                                            }
                                        }
                                    }
                                    .unindent()
                                    .add("\n)")
                                    .build()
                            )
                            .build()
                    )
                }
            }
            .build()
    }
}
