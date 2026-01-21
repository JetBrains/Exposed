package org.jetbrains.exposed.v1.iface.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.validate
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import org.jetbrains.exposed.v1.iface.ksp.generators.CompanionGenerator
import org.jetbrains.exposed.v1.iface.ksp.generators.DataClassGenerator
import org.jetbrains.exposed.v1.iface.ksp.generators.EntityImplGenerator
import org.jetbrains.exposed.v1.iface.ksp.generators.TableGenerator
import org.jetbrains.exposed.v1.iface.ksp.model.EntityInfo
import org.jetbrains.exposed.v1.iface.ksp.model.PropertyInfo
import org.jetbrains.exposed.v1.iface.ksp.validation.EntityValidator

/**
 * KSP processor for Interface DAO.
 *
 * Processes interfaces annotated with @ExposedEntity and generates:
 * - Table objects
 * - Entity implementation classes
 * - Data class variants
 * - Companion object extensions
 */
class InterfaceDaoSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val exposedEntityAnnotation = "org.jetbrains.exposed.v1.iface.annotations.ExposedEntity"

        val symbols = resolver.getSymbolsWithAnnotation(exposedEntityAnnotation)
        val validSymbols = symbols.filter { it.validate() }.toList()

        validSymbols
            .filterIsInstance<KSClassDeclaration>()
            .forEach { classDeclaration ->
                try {
                    processEntity(classDeclaration)
                } catch (e: Exception) {
                    logger.error("Error processing ${classDeclaration.simpleName.asString()}: ${e.message}", classDeclaration)
                    logger.exception(e)
                }
            }

        return symbols.filterNot { it.validate() }.toList()
    }

    private fun processEntity(classDeclaration: KSClassDeclaration) {
        logger.info("Processing entity: ${classDeclaration.simpleName.asString()}")

        // Validate the entity
        EntityValidator.validate(classDeclaration, logger)

        // Extract entity information
        val entityInfo = extractEntityInfo(classDeclaration)

        // Generate code
        generateTable(entityInfo)
        generateEntityImpl(entityInfo)
        generateCompanionExtensions(entityInfo)

        if (entityInfo.generateDataClass) {
            generateDataClass(entityInfo)
        }

        logger.info("Successfully generated code for ${entityInfo.interfaceName}")
    }

    private fun extractEntityInfo(classDeclaration: KSClassDeclaration): EntityInfo {
        val annotation = classDeclaration.annotations.first {
            it.shortName.asString() == "ExposedEntity"
        }

        val packageName = classDeclaration.packageName.asString()
        val interfaceName = classDeclaration.simpleName.asString()

        // Extract annotation parameters
        val tableNameParam = annotation.arguments.find { it.name?.asString() == "tableName" }?.value as? String
        val tableName = if (tableNameParam.isNullOrEmpty()) {
            inferTableName(interfaceName)
        } else {
            tableNameParam
        }
        val schema = annotation.arguments.find { it.name?.asString() == "schema" }?.value as? String ?: ""
        val generateDataClass = annotation.arguments.find { it.name?.asString() == "generateDataClass" }?.value as? Boolean
            ?: true

        // Extract properties
        val properties = extractProperties(classDeclaration)
        val idProperty = properties.firstOrNull { it.isPrimaryKey }
            ?: properties.firstOrNull { it.name == "id" }
            ?: createSyntheticIdProperty() // Create synthetic id if not declared

        // Check if interface has companion object
        val hasCompanionObject = classDeclaration.declarations
            .filterIsInstance<KSClassDeclaration>()
            .any { it.isCompanionObject }

        return EntityInfo(
            declaration = classDeclaration,
            packageName = packageName,
            interfaceName = interfaceName,
            tableName = tableName,
            schema = schema,
            generateDataClass = generateDataClass,
            properties = properties,
            idProperty = idProperty,
            hasCompanionObject = hasCompanionObject
        )
    }

    private fun extractProperties(classDeclaration: KSClassDeclaration): List<PropertyInfo> {
        return classDeclaration.getAllProperties()
            .filter { it.validate() }
            .map { property ->
                val name = property.simpleName.asString()
                val typeName = property.type.resolve().toTypeName()
                val isNullable = property.type.resolve().isMarkedNullable

                // Check for @PrimaryKey annotation
                val primaryKeyAnnotation = property.annotations.firstOrNull {
                    it.shortName.asString() == "PrimaryKey"
                }
                val isPrimaryKey = primaryKeyAnnotation != null || name == "id"
                val autoIncrement = primaryKeyAnnotation?.arguments?.find { it.name?.asString() == "autoIncrement" }?.value as? Boolean
                    ?: true

                // Check for @Column annotation
                val columnAnnotation = property.annotations.firstOrNull {
                    it.shortName.asString() == "Column"
                }
                val columnName = columnAnnotation?.arguments?.find { it.name?.asString() == "name" }?.value as? String
                    ?: primaryKeyAnnotation?.arguments?.find { it.name?.asString() == "columnName" }?.value as? String
                    ?: toSnakeCase(name)

                // Extract type-specific configuration
                val typeConfig = extractTypeConfig(property)

                // Extract default configuration
                val defaultConfig = extractDefaultConfig(property)

                // Extract constraints
                val constraints = extractConstraints(property)

                PropertyInfo(
                    declaration = property,
                    name = name,
                    typeName = typeName,
                    columnName = columnName,
                    isMutable = property.isMutable,
                    isNullable = isNullable,
                    isPrimaryKey = isPrimaryKey,
                    autoIncrement = autoIncrement,
                    typeConfig = typeConfig,
                    defaultConfig = defaultConfig,
                    constraints = constraints
                )
            }
            .toList()
    }

    private fun extractTypeConfig(property: KSPropertyDeclaration): org.jetbrains.exposed.v1.iface.ksp.model.TypeConfig? {
        property.annotations.forEach { annotation ->
            when (annotation.shortName.asString()) {
                "Varchar" -> {
                    val length = annotation.arguments.find { it.name?.asString() == "length" }?.value as? Int ?: 255
                    return org.jetbrains.exposed.v1.iface.ksp.model.TypeConfig.VarcharConfig(length)
                }
                "Text" -> {
                    return org.jetbrains.exposed.v1.iface.ksp.model.TypeConfig.TextConfig
                }
                "Decimal" -> {
                    val precision = annotation.arguments.find { it.name?.asString() == "precision" }?.value as? Int ?: 10
                    val scale = annotation.arguments.find { it.name?.asString() == "scale" }?.value as? Int ?: 2
                    return org.jetbrains.exposed.v1.iface.ksp.model.TypeConfig.DecimalConfig(precision, scale)
                }
                "Binary" -> {
                    val length = annotation.arguments.find { it.name?.asString() == "length" }?.value as? Int ?: 1024
                    return org.jetbrains.exposed.v1.iface.ksp.model.TypeConfig.BinaryConfig(length)
                }
            }
        }
        return null
    }

    private fun extractDefaultConfig(property: KSPropertyDeclaration): org.jetbrains.exposed.v1.iface.ksp.model.DefaultConfig? {
        property.annotations.forEach { annotation ->
            when (annotation.shortName.asString()) {
                "ClientDefault" -> {
                    val value = annotation.arguments.find { it.name?.asString() == "value" }?.value as? String ?: ""
                    return org.jetbrains.exposed.v1.iface.ksp.model.DefaultConfig.ClientDefault(value)
                }
                "DefaultExpression" -> {
                    val expression = annotation.arguments.find { it.name?.asString() == "expression" }?.value as? String ?: ""
                    return org.jetbrains.exposed.v1.iface.ksp.model.DefaultConfig.DefaultExpression(expression)
                }
                "CurrentTimestamp" -> {
                    return org.jetbrains.exposed.v1.iface.ksp.model.DefaultConfig.CurrentTimestamp
                }
                "CurrentDate" -> {
                    return org.jetbrains.exposed.v1.iface.ksp.model.DefaultConfig.CurrentDate
                }
                "CurrentDateTime" -> {
                    return org.jetbrains.exposed.v1.iface.ksp.model.DefaultConfig.CurrentDateTime
                }
            }
        }
        return null
    }

    private fun extractConstraints(property: KSPropertyDeclaration): Set<org.jetbrains.exposed.v1.iface.ksp.model.ConstraintType> {
        val constraints = mutableSetOf<org.jetbrains.exposed.v1.iface.ksp.model.ConstraintType>()
        property.annotations.forEach { annotation ->
            when (annotation.shortName.asString()) {
                "Unique" -> constraints.add(org.jetbrains.exposed.v1.iface.ksp.model.ConstraintType.UNIQUE)
                "Index" -> constraints.add(org.jetbrains.exposed.v1.iface.ksp.model.ConstraintType.INDEXED)
            }
        }
        return constraints
    }

    private fun generateTable(entityInfo: EntityInfo) {
        val fileSpec = TableGenerator(entityInfo).generate()
        fileSpec.writeTo(codeGenerator, aggregating = false)
    }

    private fun generateEntityImpl(entityInfo: EntityInfo) {
        val fileSpec = EntityImplGenerator(entityInfo).generate()
        fileSpec.writeTo(codeGenerator, aggregating = false)
    }

    private fun generateDataClass(entityInfo: EntityInfo) {
        val fileSpec = DataClassGenerator(entityInfo).generate()
        fileSpec.writeTo(codeGenerator, aggregating = false)
    }

    private fun generateCompanionExtensions(entityInfo: EntityInfo) {
        val fileSpec = CompanionGenerator(entityInfo).generate()
        fileSpec.writeTo(codeGenerator, aggregating = false)
    }

    private fun inferTableName(interfaceName: String): String {
        val baseName = if (interfaceName.startsWith("I") && interfaceName.length > 1) {
            interfaceName.substring(1)
        } else {
            interfaceName
        }
        return toSnakeCase(baseName)
    }

    private fun toSnakeCase(name: String): String {
        return name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()
    }

    /**
     * Creates a synthetic id property for entities that don't declare one.
     * This is necessary because IntEntity provides the id property.
     */
    private fun createSyntheticIdProperty(): PropertyInfo {
        return PropertyInfo(
            declaration = null, // No actual declaration
            name = "id",
            typeName = INT,
            columnName = "id",
            isMutable = false,
            isNullable = false,
            isPrimaryKey = true,
            autoIncrement = true,
            typeConfig = null,
            defaultConfig = null,
            constraints = emptySet()
        )
    }
}
