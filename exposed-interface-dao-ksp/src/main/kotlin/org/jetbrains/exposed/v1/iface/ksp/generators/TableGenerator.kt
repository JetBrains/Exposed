package org.jetbrains.exposed.v1.iface.ksp.generators

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.jetbrains.exposed.v1.iface.ksp.model.ColumnType
import org.jetbrains.exposed.v1.iface.ksp.model.EntityInfo

/**
 * Generates Table object from entity interface.
 *
 * Example output:
 * ```kotlin
 * object UserTable : IntIdTable("users") {
 *     val name = varchar("name", 255)
 *     val email = varchar("email", 255)
 * }
 * ```
 */
class TableGenerator(private val entityInfo: EntityInfo) {

    fun generate(): FileSpec {
        val tableType = TypeSpec.objectBuilder(entityInfo.tableClassName)
            .superclass(getTableSuperclass())
            .addSuperclassConstructorParameter("%S", entityInfo.tableName)
            .apply {
                // Add non-id columns
                entityInfo.properties
                    .filterNot { it.isPrimaryKey }
                    .forEach { property ->
                        addProperty(generateColumnProperty(property))
                    }

                // Add indices if any
                val indexedProperties = entityInfo.properties.filter {
                    org.jetbrains.exposed.v1.iface.ksp.model.ConstraintType.INDEXED in it.constraints &&
                    org.jetbrains.exposed.v1.iface.ksp.model.ConstraintType.UNIQUE !in it.constraints
                }
                if (indexedProperties.isNotEmpty()) {
                    addInitializerBlock(CodeBlock.builder().apply {
                        indexedProperties.forEach { property ->
                            addStatement("index(false, %N)", property.name)
                        }
                    }.build())
                }
            }
            .build()

        return FileSpec.builder(entityInfo.packageName, entityInfo.tableClassName)
            .addType(tableType)
            .addImport("org.jetbrains.exposed.v1.core.dao.id", "IntIdTable", "LongIdTable")
            .build()
    }

    private fun getTableSuperclass(): ClassName {
        return when (entityInfo.idProperty.typeName.toString().removeSuffix("?")) {
            "kotlin.Int", "Int" -> ClassName("org.jetbrains.exposed.v1.core.dao.id", "IntIdTable")
            "kotlin.Long", "Long" -> ClassName("org.jetbrains.exposed.v1.core.dao.id", "LongIdTable")
            else -> ClassName("org.jetbrains.exposed.v1.core.dao.id", "IntIdTable") // Default to Int
        }
    }

    private fun generateColumnProperty(property: org.jetbrains.exposed.v1.iface.ksp.model.PropertyInfo): PropertySpec {
        val columnClass = ClassName("org.jetbrains.exposed.v1.core", "Column")
        val propertyBuilder = PropertySpec.builder(property.name, columnClass.parameterizedBy(property.typeName))

        val codeBlock = CodeBlock.builder()

        // Generate column function call with type-specific parameters
        when (val colType = property.columnType) {
            is ColumnType.Integer -> codeBlock.add("integer(%S)", property.columnName)
            is ColumnType.Long -> codeBlock.add("long(%S)", property.columnName)
            is ColumnType.Varchar -> codeBlock.add("varchar(%S, %L)", property.columnName, colType.length)
            is ColumnType.Text -> codeBlock.add("text(%S)", property.columnName)
            is ColumnType.Bool -> codeBlock.add("bool(%S)", property.columnName)
            is ColumnType.Float -> codeBlock.add("float(%S)", property.columnName)
            is ColumnType.Double -> codeBlock.add("double(%S)", property.columnName)
            is ColumnType.Decimal -> codeBlock.add("decimal(%S, %L, %L)", property.columnName, colType.precision, colType.scale)
            is ColumnType.Binary -> codeBlock.add("binary(%S, %L)", property.columnName, colType.length)
            is ColumnType.Timestamp -> codeBlock.add("timestamp(%S)", property.columnName)
            is ColumnType.Date -> codeBlock.add("date(%S)", property.columnName)
            is ColumnType.DateTime -> codeBlock.add("datetime(%S)", property.columnName)
        }

        // Add nullable if needed
        if (property.isNullable) {
            codeBlock.add(".nullable()")
        }

        // Add unique constraint
        if (org.jetbrains.exposed.v1.iface.ksp.model.ConstraintType.UNIQUE in property.constraints) {
            codeBlock.add(".uniqueIndex()")
        }

        // Add default expression/value
        when (val defaultCfg = property.defaultConfig) {
            is org.jetbrains.exposed.v1.iface.ksp.model.DefaultConfig.CurrentTimestamp -> {
                codeBlock.add(".defaultExpression(CurrentTimestamp)")
            }
            is org.jetbrains.exposed.v1.iface.ksp.model.DefaultConfig.CurrentDate -> {
                codeBlock.add(".defaultExpression(CurrentDate)")
            }
            is org.jetbrains.exposed.v1.iface.ksp.model.DefaultConfig.CurrentDateTime -> {
                codeBlock.add(".defaultExpression(CurrentDateTime)")
            }
            is org.jetbrains.exposed.v1.iface.ksp.model.DefaultConfig.DefaultExpression -> {
                // Map common expression names to Exposed's Expression classes
                val expressionCode = when (defaultCfg.expression) {
                    "CurrentTimestamp" -> "CurrentTimestamp"
                    "CurrentDate" -> "CurrentDate"
                    "CurrentDateTime" -> "CurrentDateTime"
                    "Random" -> "Random()"
                    else -> {
                        // For literal values, use clientDefault
                        codeBlock.add(".clientDefault { %L }", defaultCfg.expression)
                        null
                    }
                }
                if (expressionCode != null) {
                    codeBlock.add(".defaultExpression(%L)", expressionCode)
                }
            }
            is org.jetbrains.exposed.v1.iface.ksp.model.DefaultConfig.ClientDefault -> {
                // Client default - will be applied in entity creation
                codeBlock.add(".clientDefault { %L }", defaultCfg.value)
            }
            null -> {} // No default
        }

        propertyBuilder.initializer(codeBlock.build())

        return propertyBuilder.build()
    }
}
