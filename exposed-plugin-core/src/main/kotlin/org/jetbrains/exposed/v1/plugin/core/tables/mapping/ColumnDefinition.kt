package org.jetbrains.exposed.v1.plugin.core.tables.mapping

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import java.util.UUID

/**
 * Stores all column metadata pertaining to both the database column being mapped, and the generated `Column`,
 * its associated Kotlin type, and its arguments for invocation.
 */
internal data class ColumnDefinition(
    /** Name of the Exposed `Table` method that returns a `Column`, for example `varchar`. */
    val function: String,

    /**
     * The non-nullable Kotlin type parameter of the generated `Column`, or `null` when the column's code is configured
     * and the generated property leaves its type to Kotlin's inference.
     */
    val typeName: TypeName?,

    /** Any arguments that follow the column name in the function call, for example `255` in `varchar("COL", 255)`. */
    val arguments: CodeBlock = CodeBlock.of(""),

    /**
     * Package of the Exposed `Table` method when it is a top-level extension function that must be imported,
     * or an empty string when it is a `Table` member function.
     */
    val functionPackage: String = "",

    /** An opt-in marker annotation that a file using [typeName] must declare, if any. */
    val requiredOptIn: ClassName? = null,

    /**
     * A type argument passed explicitly to the Exposed `Table` method,
     * for functions whose type cannot be inferred from [arguments].
     */
    val typeArgument: TypeName? = null,

    /**
     * A log message of how the column was mapped when the database could not provide enough information to choose.
     * The user is expected to review the logs, but the message is also added as KDoc to the generated property.
     */
    val mappingLog: String? = null,

    /** The Exposed module, other than `exposed-core`, that generated code using this definition depends on, if any. */
    val requiredModule: ExposedModule? = null,

    /**
     * The constructor call of the `ColumnType` that [function] registers, for example `VarCharColumnType(255)`.
     * This will be used wherever a column type must be passed explicitly, such as for arrays.
     * Set to `null` when the column cannot be an array element.
     */
    val columnTypeExpression: CodeBlock? = null,

    /**
     * A type that must be declared inside the generated table object for the column to compile, such as the enum
     * class of a `customEnumeration()` column; [typeName] refers to it by simple name.
     */
    val nestedType: TypeSpec? = null,

    /**
     * The Exposed `Table` method configured by the user through `columnMappings`.
     * When not `null`, this will be used unchanged instead of [function] and [arguments].
     */
    val mappedCode: CodeBlock? = null,

    /** Fully qualified names that [mappedCode] needs imported into the generated file for the column to compile. */
    val imports: List<String> = emptyList(),
) {
    /** Whether the column's code was configured rather than generated */
    val isMapped: Boolean
        get() = mappedCode != null

    /**
     * Whether the Exposed `Table` method is a top-level extension function that must be imported,
     * rather than a `Table` member.
     */
    val isExtensionFunction: Boolean
        get() = functionPackage.isNotEmpty()

    /** The Exposed `Table` method as a KotlinPoet member reference, for use when [isExtensionFunction] is `true`. */
    val memberName: MemberName
        get() = MemberName(functionPackage, function)

    val supportsSequenceAutoIncrement: Boolean
        get() = typeName == INT_TYPE_NAME || typeName == LONG_TYPE_NAME

    /**
     * Renders the call that declares a column named [columnName] with this definition, for example `varchar("NAME", 255)`.
     * `Table` member functions are referenced by name only; a KotlinPoet member reference would emit a bogus import for them.
     */
    fun factoryCall(columnName: String): CodeBlock = mappedCode ?: CodeBlock.builder().apply {
        if (isExtensionFunction) add("%M", memberName) else add("%N", function)
        typeArgument?.let { add("<%T>", it) }
        add("(%S", columnName)
        add(arguments)
        add(")")
    }.build()
}

/**
 * Every Exposed `Table` method that returns a `Column`, which the generator is aware of.
 * Every comparison with a [ColumnDefinition.function] goes through these constants.
 */
internal object ColumnFunctions {
    const val BYTE: String = "byte"
    const val UBYTE: String = "ubyte"
    const val SHORT: String = "short"
    const val USHORT: String = "ushort"
    const val INTEGER: String = "integer"
    const val UINTEGER: String = "uinteger"
    const val LONG: String = "long"
    const val ULONG: String = "ulong"
    const val FLOAT: String = "float"
    const val DOUBLE: String = "double"
    const val DECIMAL: String = "decimal"
    const val BOOL: String = "bool"
    const val CHAR: String = "char"
    const val VARCHAR: String = "varchar"
    const val TEXT: String = "text"
    const val MEDIUM_TEXT: String = "mediumText"
    const val LARGE_TEXT: String = "largeText"
    const val BINARY: String = "binary"
    const val BLOB: String = "blob"
    const val UUID: String = "uuid"
    const val JAVA_UUID: String = "javaUUID"
    const val JSON: String = "json"
    const val JSONB: String = "jsonb"
    const val ARRAY: String = "array"
    const val VECTOR: String = "vector"
    const val CUSTOM_ENUMERATION: String = "customEnumeration"
    const val DATE: String = "date"
    const val TIME: String = "time"
    const val DATETIME: String = "datetime"
    const val TIMESTAMP: String = "timestamp"
    const val TIMESTAMP_WITH_TIME_ZONE: String = "timestampWithTimeZone"
    const val DURATION: String = "duration"
}

/** An Exposed module other than `exposed-core`, whose column functions generated code may depend on. */
internal data class ExposedModule(
    /** Maven artifact name of the module, for log messages. */
    val artifactName: String,
    /** Path of a class resource that is present only when the module is on a classpath, for detection. */
    val markerResource: String,
) {
    fun isOnClasspath(classpath: ClassLoader): Boolean = classpath.getResource(markerResource) != null

    companion object {
        val JSON: ExposedModule = ExposedModule(
            "exposed-json",
            "org/jetbrains/exposed/v1/json/JsonColumnType.class"
        )
        val KOTLIN_DATETIME: ExposedModule = ExposedModule(
            "exposed-kotlin-datetime",
            "org/jetbrains/exposed/v1/datetime/KotlinLocalDateColumnType.class"
        )
        val JAVA_TIME: ExposedModule = ExposedModule(
            "exposed-java-time",
            "org/jetbrains/exposed/v1/javatime/JavaLocalDateColumnType.class"
        )
        val JODA_TIME: ExposedModule = ExposedModule(
            "exposed-jodatime",
            "org/jetbrains/exposed/v1/jodatime/JodaLocalDateColumnType.class"
        )
    }
}

internal val UUID_TYPE_NAME: ClassName = ClassName("kotlin.uuid", "Uuid")

internal val JAVA_UUID_TYPE_NAME: ClassName = UUID::class.asTypeName()

internal val INT_TYPE_NAME: TypeName = Int::class.asTypeName()

internal val LONG_TYPE_NAME: TypeName = Long::class.asTypeName()
