package org.jetbrains.exposed.v1.iface.ksp.generators

import com.squareup.kotlinpoet.*
import org.jetbrains.exposed.v1.iface.ksp.model.EntityInfo

/**
 * Generates immutable data class variant from interface.
 *
 * Example output:
 * ```kotlin
 * data class UserData(
 *     override val id: Int,
 *     override val name: String,
 *     override val email: String
 * ) : IUser
 * ```
 */
class DataClassGenerator(private val entityInfo: EntityInfo) {

    fun generate(): FileSpec {
        val interfaceType = ClassName(entityInfo.packageName, entityInfo.interfaceName)

        val dataClass = TypeSpec.classBuilder(entityInfo.dataClassName)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .apply {
                        // Add id property first
                        addParameter(
                            ParameterSpec.builder("id", entityInfo.idProperty.typeName)
                                .build()
                        )
                        // Add other properties (excluding id if it was in the interface)
                        entityInfo.properties.filterNot { it.isPrimaryKey }.forEach { property ->
                            addParameter(
                                ParameterSpec.builder(property.name, property.typeName)
                                    .build()
                            )
                        }
                    }
                    .build()
            )
            .apply {
                // Add id property first
                addProperty(
                    PropertySpec.builder("id", entityInfo.idProperty.typeName)
                        .mutable(false)
                        .initializer("id")
                        .build()
                )
                // Add other properties (all immutable in data class)
                entityInfo.properties.filterNot { it.isPrimaryKey }.forEach { property ->
                    addProperty(
                        PropertySpec.builder(property.name, property.typeName)
                            .mutable(false)  // Data classes should be immutable
                            .initializer(property.name)
                            .build()
                    )
                }
            }
            .build()

        return FileSpec.builder(entityInfo.packageName, entityInfo.dataClassName)
            .addType(dataClass)
            .build()
    }
}
