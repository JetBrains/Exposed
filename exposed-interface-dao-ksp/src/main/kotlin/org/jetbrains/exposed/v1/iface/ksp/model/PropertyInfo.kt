package org.jetbrains.exposed.v1.iface.ksp.model

import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.kotlinpoet.TypeName

/**
 * Holds information about a property in an entity interface.
 *
 * @property declaration The KSP declaration of the property (null for synthetic properties).
 * @property name The property name (e.g., "name").
 * @property typeName The Kotlin type of the property.
 * @property columnName The database column name (e.g., "user_name").
 * @property isMutable Whether the property is var (true) or val (false).
 * @property isNullable Whether the type is nullable.
 * @property isPrimaryKey Whether this is the primary key property.
 * @property autoIncrement Whether primary key auto-increments.
 * @property typeConfig Type-specific configuration (varchar length, decimal precision, etc.).
 * @property defaultConfig Default value configuration.
 * @property constraints Constraint annotations (unique, indexed).
 */
data class PropertyInfo(
    val declaration: KSPropertyDeclaration?,
    val name: String,
    val typeName: TypeName,
    val columnName: String,
    val isMutable: Boolean,
    val isNullable: Boolean,
    val isPrimaryKey: Boolean = false,
    val autoIncrement: Boolean = true,
    val typeConfig: TypeConfig? = null,
    val defaultConfig: DefaultConfig? = null,
    val constraints: Set<ConstraintType> = emptySet()
) {
    /**
     * The column type for code generation.
     */
    val columnType: ColumnType
        get() = ColumnType.fromTypeName(typeName, isNullable, typeConfig)
}

/**
 * Type-specific configuration from annotations like @Varchar, @Decimal, etc.
 */
sealed class TypeConfig {
    data class VarcharConfig(val length: Int) : TypeConfig()
    data object TextConfig : TypeConfig()
    data class DecimalConfig(val precision: Int, val scale: Int) : TypeConfig()
    data class BinaryConfig(val length: Int) : TypeConfig()
}

/**
 * Default value configuration.
 */
sealed class DefaultConfig {
    /**
     * Client-side default (applied in code).
     */
    data class ClientDefault(val value: String) : DefaultConfig()

    /**
     * Database-level default expression.
     */
    data class DefaultExpression(val expression: String) : DefaultConfig()

    /**
     * Convenience defaults.
     */
    data object CurrentTimestamp : DefaultConfig()
    data object CurrentDate : DefaultConfig()
    data object CurrentDateTime : DefaultConfig()
}

/**
 * Constraint types.
 */
enum class ConstraintType {
    UNIQUE,
    INDEXED
}

/**
 * Represents a column type for code generation.
 */
sealed class ColumnType {
    data class Integer(val nullable: Boolean) : ColumnType()
    data class Long(val nullable: Boolean) : ColumnType()
    data class Varchar(val length: Int, val nullable: Boolean) : ColumnType()
    data class Text(val nullable: Boolean) : ColumnType()
    data class Bool(val nullable: Boolean) : ColumnType()
    data class Float(val nullable: Boolean) : ColumnType()
    data class Double(val nullable: Boolean) : ColumnType()
    data class Decimal(val precision: Int, val scale: Int, val nullable: Boolean) : ColumnType()
    data class Binary(val length: Int, val nullable: Boolean) : ColumnType()
    data class Timestamp(val nullable: Boolean) : ColumnType()
    data class Date(val nullable: Boolean) : ColumnType()
    data class DateTime(val nullable: Boolean) : ColumnType()

    companion object {
        fun fromTypeName(typeName: TypeName, nullable: Boolean, typeConfig: TypeConfig?): ColumnType {
            val typeString = typeName.toString()
                .removePrefix("kotlin.")
                .removePrefix("java.time.")
                .removeSuffix("?")

            // Check type-specific configuration first
            when (typeConfig) {
                is TypeConfig.VarcharConfig -> return Varchar(typeConfig.length, nullable)
                is TypeConfig.TextConfig -> return Text(nullable)
                is TypeConfig.DecimalConfig -> return Decimal(typeConfig.precision, typeConfig.scale, nullable)
                is TypeConfig.BinaryConfig -> return Binary(typeConfig.length, nullable)
                null -> {} // Fall through to default type mapping
            }

            // Default type mapping based on Kotlin type
            return when (typeString) {
                "Int" -> Integer(nullable)
                "Long" -> Long(nullable)
                "String" -> Varchar(255, nullable) // Default varchar length
                "Boolean" -> Bool(nullable)
                "Float" -> Float(nullable)
                "Double" -> Double(nullable)
                "java.math.BigDecimal", "BigDecimal" -> Decimal(10, 2, nullable) // Default precision/scale
                "ByteArray" -> Binary(1024, nullable) // Default binary length
                "Instant" -> Timestamp(nullable)
                "LocalDate" -> Date(nullable)
                "LocalDateTime" -> DateTime(nullable)
                else -> Varchar(255, nullable) // Fallback
            }
        }
    }
}
