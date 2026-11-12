package org.jetbrains.exposed.v1.plugin.core.tables.mapping

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.core.vendors.ColumnMetadata
import org.jetbrains.exposed.v1.plugin.core.tables.*
import org.jetbrains.exposed.v1.plugin.core.tables.schema.EnumerationType
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.ArrayElement
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.DialectSyntax
import java.math.BigDecimal
import java.sql.Types

private const val CORE_PACKAGE: String = "org.jetbrains.exposed.v1.core"

/** Renders the constructor call of the `ColumnType` class [simpleName] in [packageName] with [arguments]. */
internal fun columnType(
    simpleName: String,
    vararg arguments: Any,
    packageName: String = CORE_PACKAGE
): CodeBlock =
    CodeBlock.of("%T(${arguments.joinToString { "%L" }})", ClassName(packageName, simpleName), *arguments)

/** A column's SQL type reduced to the parts that the mapping needs: the base type name and its length/precision and scale arguments. */
private data class SqlType(val baseType: String, val length: Int?, val scale: Int?)

/**
 * Maps the metadata of a column to the Exposed column factory function that best represents it,
 * or returns `null` if the column's type is not supported by this generator.
 *
 * The normalized [sqlType] is used when the dialect reports one (see `DialectSchemaSupport`), because
 * it is the same string that Exposed compares against a column's `sqlType()` during migrations, so matching on it keeps
 * generated definitions free of migration differences. The JDBC [jdbcType] is used otherwise.
 *
 * Only basic, date/time, JSON and enumeration types, and one-dimensional arrays of the basic and date/time types,
 * are supported. Vector and vendor-specific types are not mapped.
 *
 * @param syntax How the database spells array and enumeration types, and what size it reports for an unbounded column.
 * @param uuidColumnType Which Exposed function a database `UUID` column maps to.
 * @param dateTimeColumnType Which Exposed date/time module the functions for date/time columns are taken from.
 * @param enumeration The named enumeration type of the column, on a database that declares enumerations as types.
 */
internal fun ColumnMetadata.toColumnDefinition(
    syntax: DialectSyntax,
    uuidColumnType: UuidColumnType = UuidColumnType.KOTLIN,
    dateTimeColumnType: DateTimeColumnType = DateTimeColumnType.KOTLIN_DATETIME,
    enumeration: EnumerationType? = null,
): ColumnDefinition? {
    enumeration?.let { return toCustomEnumerationDefinition(it.sql, it.values) }
    // the enumeration literal keeps its case, since its values become enum constants
    val reportedType = sqlType.trim()
    syntax.enumerationValues(reportedType)?.let { values ->
        return toCustomEnumerationDefinition(reportedType, values)
    }
    val declaredType = reportedType.uppercase()
    syntax.arrayElement(declaredType)?.let { element ->
        return toArrayDefinition(element, uuidColumnType, dateTimeColumnType)
    }
    val type = declaredType.toSqlType() ?: jdbcSqlType(syntax.unboundedLength) ?: return null
    return toColumnDefinition(type, uuidColumnType, dateTimeColumnType)
}

/**
 * Matches a normalized SQL type such as `VARCHAR(255)`, `NUMERIC(10, 2)`, `DOUBLE PRECISION`, `CHAR` or
 * `TIMESTAMP(9) WITH TIME ZONE`, capturing the base type name, its optional length/precision and scale arguments,
 * and any trailing keywords after the arguments.
 */
private val SQL_TYPE_PATTERN: Regex = Regex("""^([A-Z][A-Z ]*?)\s*(?:\((\d+)(?:,\s*(\d+))?\))?((?: [A-Z]+)*)$""")

/** Parses this normalized, upper-cased declared type, or returns `null` when it is empty or not of the expected form. */
private fun String.toSqlType(): SqlType? {
    val match = SQL_TYPE_PATTERN.matchEntire(this) ?: return null
    val (baseType, length, scale, suffix) = match.destructured
    return SqlType((baseType.trim() + suffix).trim(), length.toIntOrNull(), scale.toIntOrNull())
}

/**
 * Derives the SQL type from the JDBC [jdbcType] and size, for dialects that report no declared type.
 * A character or binary size at or above [unboundedLength] is treated as no length at all.
 */
@Suppress("CyclomaticComplexMethod")
private fun ColumnMetadata.jdbcSqlType(unboundedLength: Int): SqlType? {
    val baseType = when (jdbcType) {
        Types.TINYINT -> "TINYINT"
        Types.SMALLINT -> "SMALLINT"
        Types.INTEGER -> "INTEGER"
        Types.BIGINT -> "BIGINT"
        Types.NUMERIC, Types.DECIMAL -> "NUMERIC"
        Types.REAL, Types.FLOAT -> "FLOAT"
        Types.DOUBLE -> "DOUBLE PRECISION"
        Types.BOOLEAN, Types.BIT -> "BOOLEAN"
        Types.CHAR -> "CHAR"
        Types.VARCHAR -> "VARCHAR"
        Types.LONGVARCHAR, Types.CLOB -> "CLOB"
        Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> "VARBINARY"
        Types.BLOB -> "BLOB"
        Types.DATE -> "DATE"
        Types.TIME -> "TIME"
        Types.TIMESTAMP -> "TIMESTAMP"
        Types.TIMESTAMP_WITH_TIMEZONE -> "TIMESTAMP WITH TIME ZONE"
        else -> return null
    }
    val length = when (jdbcType) {
        // JDBC reports no distinct type for a single-character column
        Types.CHAR -> size ?: 1
        Types.NUMERIC, Types.DECIMAL -> size
        else -> size?.takeIf { it < unboundedLength }
    }
    return SqlType(baseType, length, scale)
}

private val BYTE = ColumnDefinition(ColumnFunctions.BYTE, Byte::class.asTypeName(), columnTypeExpression = columnType("ByteColumnType"))
private val SHORT = ColumnDefinition(ColumnFunctions.SHORT, Short::class.asTypeName(), columnTypeExpression = columnType("ShortColumnType"))
private val INTEGER = ColumnDefinition(ColumnFunctions.INTEGER, INT_TYPE_NAME, columnTypeExpression = columnType("IntegerColumnType"))
private val LONG = ColumnDefinition(ColumnFunctions.LONG, LONG_TYPE_NAME, columnTypeExpression = columnType("LongColumnType"))
private val FLOAT = ColumnDefinition(ColumnFunctions.FLOAT, Float::class.asTypeName(), columnTypeExpression = columnType("FloatColumnType"))
private val DOUBLE = ColumnDefinition(ColumnFunctions.DOUBLE, Double::class.asTypeName(), columnTypeExpression = columnType("DoubleColumnType"))
private val BOOL = ColumnDefinition(ColumnFunctions.BOOL, Boolean::class.asTypeName(), columnTypeExpression = columnType("BooleanColumnType"))
private val SINGLE_CHAR = ColumnDefinition(ColumnFunctions.CHAR, Char::class.asTypeName(), columnTypeExpression = columnType("CharacterColumnType"))
private val TEXT = ColumnDefinition(ColumnFunctions.TEXT, String::class.asTypeName(), columnTypeExpression = columnType("TextColumnType"))
private val BINARY = ColumnDefinition(ColumnFunctions.BINARY, ByteArray::class.asTypeName(), columnTypeExpression = columnType("BasicBinaryColumnType"))
private val BLOB = ColumnDefinition(ColumnFunctions.BLOB, ExposedBlob::class.asTypeName(), columnTypeExpression = columnType("BlobColumnType"))

private fun decimal(precision: Int, scale: Int) = ColumnDefinition(
    ColumnFunctions.DECIMAL,
    BigDecimal::class.asTypeName(),
    CodeBlock.of(", %L, %L", precision, scale),
    columnTypeExpression = columnType("DecimalColumnType", precision, scale),
)

private fun char(length: Int) = ColumnDefinition(
    ColumnFunctions.CHAR,
    String::class.asTypeName(),
    CodeBlock.of(", %L", length),
    columnTypeExpression = columnType("CharColumnType", length),
)

private fun varchar(length: Int) = ColumnDefinition(
    ColumnFunctions.VARCHAR,
    String::class.asTypeName(),
    CodeBlock.of(", %L", length),
    columnTypeExpression = columnType("VarCharColumnType", length),
)

private fun binary(length: Int) = ColumnDefinition(
    ColumnFunctions.BINARY,
    ByteArray::class.asTypeName(),
    CodeBlock.of(", %L", length),
    columnTypeExpression = columnType("BinaryColumnType", length),
)

private const val JSON_FUNCTION_PACKAGE: String = "org.jetbrains.exposed.v1.json"

private val JSON = ColumnDefinition(
    ColumnFunctions.JSON,
    String::class.asTypeName(),
    CodeBlock.of(", { it }, { it }"),
    functionPackage = JSON_FUNCTION_PACKAGE,
    typeArgument = String::class.asTypeName(),
    mappingLog = "Generated as json<String>() with identity serializers, because the database does not say which Kotlin type " +
        "the JSON maps to; replace it with json<T>(name, jsonConfig) once the type is known. A database with a single JSON " +
        "format (like H2) reports a column created with jsonb() identically, so it is always generated as json() unless configured otherwise",
    requiredModule = ExposedModule.JSON,
)

private val JSONB = ColumnDefinition(
    ColumnFunctions.JSONB,
    String::class.asTypeName(),
    CodeBlock.of(", { it }, { it }"),
    functionPackage = JSON_FUNCTION_PACKAGE,
    typeArgument = String::class.asTypeName(),
    mappingLog = "Generated as jsonb<String>() with identity serializers, because the database does not say which Kotlin type " +
        "the JSON maps to; replace it with jsonb<T>(name, jsonConfig) once the type is known",
    requiredModule = ExposedModule.JSON,
)

private fun vector(dimensions: Int?) = ColumnDefinition(
    ColumnFunctions.VECTOR,
    FloatArray::class.asTypeName(),
    CodeBlock.of(", %L", dimensions?.toString() ?: "null"),
)

private val EXPERIMENTAL_UUID_API: ClassName = ClassName("kotlin.uuid", "ExperimentalUuidApi")
private const val JAVA_UUID_FUNCTION_PACKAGE: String = "$CORE_PACKAGE.java"

private fun UuidColumnType.toColumnDefinition(): ColumnDefinition = when (this) {
    UuidColumnType.KOTLIN -> ColumnDefinition(
        ColumnFunctions.UUID,
        UUID_TYPE_NAME,
        requiredOptIn = EXPERIMENTAL_UUID_API,
        columnTypeExpression = columnType("UuidColumnType"),
    )
    UuidColumnType.JAVA -> ColumnDefinition(
        ColumnFunctions.JAVA_UUID,
        JAVA_UUID_TYPE_NAME,
        functionPackage = JAVA_UUID_FUNCTION_PACKAGE,
        columnTypeExpression = columnType("UUIDColumnType", packageName = JAVA_UUID_FUNCTION_PACKAGE),
    )
}

@Suppress("CyclomaticComplexMethod")
private fun ColumnMetadata.toColumnDefinition(
    type: SqlType,
    uuidColumnType: UuidColumnType,
    dateTimeColumnType: DateTimeColumnType,
): ColumnDefinition? = when (type.baseType) {
    "UUID" -> uuidColumnType.toColumnDefinition()
    "DATE" -> dateTimeColumnType.toColumnDefinition(TemporalFunction.DATE)
    "TIME" -> dateTimeColumnType.toColumnDefinition(TemporalFunction.TIME)
    "TIMESTAMP", "DATETIME" -> dateTimeColumnType.toColumnDefinition(TemporalFunction.DATETIME)
    "TIMESTAMP WITH TIME ZONE" -> dateTimeColumnType.toColumnDefinition(TemporalFunction.TIMESTAMP_WITH_TIME_ZONE)
    "TINYINT" -> BYTE
    "SMALLINT" -> SHORT
    "INT", "INTEGER" -> INTEGER
    "BIGINT" -> LONG
    // a declared type without arguments falls back to the precision and scale that JDBC reports
    "NUMERIC", "DECIMAL" -> (type.length ?: size)?.let { precision -> decimal(precision, type.scale ?: scale ?: 0) }
    "FLOAT", "REAL" -> FLOAT
    "DOUBLE PRECISION", "DOUBLE" -> DOUBLE
    "BOOLEAN", "BIT" -> BOOL
    // a bare CHAR is Exposed's char() of a single Char; CHAR(n) is char(name, n) of String
    "CHAR" -> type.length?.let(::char) ?: SINGLE_CHAR
    "VARCHAR" -> type.length?.let(::varchar) ?: TEXT
    "CLOB", "TEXT" -> TEXT
    "VARBINARY", "BINARY" -> type.length?.let(::binary) ?: BINARY
    "BLOB" -> BLOB
    "JSON" -> JSON
    "JSONB" -> JSONB
    "VECTOR" -> vector(type.length)
    else -> null
}

private val LIST_TYPE_NAME: ClassName = ClassName("kotlin.collections", "List")

private fun ColumnMetadata.toArrayDefinition(
    arrayElement: ArrayElement,
    uuidColumnType: UuidColumnType,
    dateTimeColumnType: DateTimeColumnType,
): ColumnDefinition? {
    val elementType = arrayElement.sqlType.toSqlType() ?: return null
    val element = toColumnDefinition(elementType, uuidColumnType, dateTimeColumnType) ?: return null
    val elementColumnType = element.columnTypeExpression ?: return null

    val elementTypeName = requireNotNull(element.typeName) { "A generated element definition always has a type" }
    val arguments = CodeBlock.builder().add(", %L", elementColumnType)
    if (arrayElement.dimensions > 1) {
        arguments.add(", dimensions = %L", arrayElement.dimensions.toString())
    } else {
        arrayElement.maximumCardinality?.let { arguments.add(", %L", it) }
    }
    val listType = (1..arrayElement.dimensions)
        .fold(elementTypeName) { nested, _ -> LIST_TYPE_NAME.parameterizedBy(nested) }
    return ColumnDefinition(
        ColumnFunctions.ARRAY,
        listType,
        arguments.build(),
        requiredOptIn = element.requiredOptIn,
        requiredModule = element.requiredModule,
    )
}

private val JVM_FORBIDDEN_IDENTIFIER_CHARS: Set<Char> = setOf('.', ';', '[', ']', '/', '<', '>', ':', '\\', '`')

private fun ColumnMetadata.toCustomEnumerationDefinition(literal: String, values: List<String>): ColumnDefinition? {
    if (
        values.isEmpty() ||
        values.any { value ->
            value.isEmpty() || value.any { it in JVM_FORBIDDEN_IDENTIFIER_CHARS }
        }
    ) {
        return null
    }

    val enumName = name.toObjectName()
    val enumClass = ClassName("", enumName)
    val propertyName = name.toPropertyName()
    val enumType = TypeSpec.enumBuilder(enumName)
        .addModifiers(KModifier.INTERNAL)
        .addKdoc(
            "Generated for the customEnumeration property [%L] from the column's definition %L. " +
                "Its constants are the exact reported database values. Review the names, or replace it with an existing enum class.\n",
            propertyName,
            literal,
        )
    values.forEach { enumType.addEnumConstant(it) }

    return ColumnDefinition(
        ColumnFunctions.CUSTOM_ENUMERATION,
        enumClass,
        CodeBlock.of(", %S, { %T.valueOf(it as String) }, { it.name }", literal, enumClass),
        mappingLog = "Generated as customEnumeration() with the nested enum class $enumName, whose constants are the values of " +
            "the column's definition $literal. Review the names, or replace the class with an existing enum class.",
        nestedType = enumType.build(),
    )
}

/**
 * Builds the definition of a column whose code was configured through [mapping]. The code is used unchanged and the
 * property's type is left to Kotlin's inference. Any modifiers are still derived from the database by the builder.
 *
 * When the mapping names no imports, the import of an Exposed extension function that the generator itself knows is
 * inferred from the leading function name of the code. Anything else must be imported explicitly.
 */
internal fun ColumnMetadata.toMappedColumnDefinition(
    mapping: ColumnMapping,
    dateTimeColumnType: DateTimeColumnType,
): ColumnDefinition {
    val code = mapping.code.trim()
    val functionName = code.takeWhile { it.isLetterOrDigit() || it == '_' }
    val inferred = if (mapping.imports.isEmpty()) {
        inferredImport(functionName, dateTimeColumnType)
    } else {
        null
    }
    return ColumnDefinition(
        function = functionName.ifEmpty { "<mapped>" },
        typeName = null,
        mappedCode = CodeBlock.of("%L", code),
        imports = mapping.imports.ifEmpty { listOfNotNull(inferred?.first) },
        requiredModule = inferred?.second,
        mappingLog = "Mapped by the columnMappings configuration (key '${mapping.column}') in place of the database type '$sqlType'",
    )
}

/**
 * Used to type an `IdTable<T>` whose key column is mapped by configuration, where the mapped code alone does not say.
 */
@Suppress("CyclomaticComplexMethod")
internal fun mappedKotlinType(
    functionName: String,
    dateTimeColumnType: DateTimeColumnType,
): TypeName? = when (functionName) {
    ColumnFunctions.BYTE -> Byte::class.asTypeName()
    ColumnFunctions.UBYTE -> UByte::class.asTypeName()
    ColumnFunctions.SHORT -> Short::class.asTypeName()
    ColumnFunctions.USHORT -> UShort::class.asTypeName()
    ColumnFunctions.INTEGER -> INT_TYPE_NAME
    ColumnFunctions.UINTEGER -> UInt::class.asTypeName()
    ColumnFunctions.LONG -> LONG_TYPE_NAME
    ColumnFunctions.ULONG -> ULong::class.asTypeName()
    ColumnFunctions.FLOAT -> Float::class.asTypeName()
    ColumnFunctions.DOUBLE -> Double::class.asTypeName()
    ColumnFunctions.DECIMAL -> BigDecimal::class.asTypeName()
    ColumnFunctions.BOOL -> Boolean::class.asTypeName()
    ColumnFunctions.VARCHAR, ColumnFunctions.TEXT, ColumnFunctions.MEDIUM_TEXT, ColumnFunctions.LARGE_TEXT -> String::class.asTypeName()
    ColumnFunctions.UUID -> UUID_TYPE_NAME
    ColumnFunctions.JAVA_UUID -> JAVA_UUID_TYPE_NAME
    ColumnFunctions.DATE -> dateTimeColumnType.typeNameOf(TemporalFunction.DATE)
    ColumnFunctions.TIME -> dateTimeColumnType.typeNameOf(TemporalFunction.TIME)
    ColumnFunctions.DATETIME -> dateTimeColumnType.typeNameOf(TemporalFunction.DATETIME)
    ColumnFunctions.TIMESTAMP_WITH_TIME_ZONE -> dateTimeColumnType.typeNameOf(TemporalFunction.TIMESTAMP_WITH_TIME_ZONE)
    else -> null
}

private val DATE_TIME_FUNCTIONS: Set<String> = setOf(
    ColumnFunctions.DATE,
    ColumnFunctions.TIME,
    ColumnFunctions.DATETIME,
    ColumnFunctions.TIMESTAMP,
    ColumnFunctions.TIMESTAMP_WITH_TIME_ZONE,
    ColumnFunctions.DURATION,
)

private fun inferredImport(
    functionName: String,
    dateTimeColumnType: DateTimeColumnType,
): Pair<String, ExposedModule?>? = when (functionName) {
    in DATE_TIME_FUNCTIONS -> "${dateTimeColumnType.functionPackage}.$functionName" to dateTimeColumnType.module
    ColumnFunctions.JSON, ColumnFunctions.JSONB -> "$JSON_FUNCTION_PACKAGE.$functionName" to ExposedModule.JSON
    ColumnFunctions.JAVA_UUID -> "$JAVA_UUID_FUNCTION_PACKAGE.$functionName" to null
    else -> null
}
