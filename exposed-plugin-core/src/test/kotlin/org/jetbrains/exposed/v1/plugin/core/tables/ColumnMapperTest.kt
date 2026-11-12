package org.jetbrains.exposed.v1.plugin.core.tables

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.asTypeName
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.core.vendors.ColumnMetadata
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.ColumnDefinition
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.ExposedModule
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.JAVA_UUID_TYPE_NAME
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.UUID_TYPE_NAME
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.toColumnDefinition
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.H2Syntax
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.Types
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColumnMapperTest {
    // Tests the mapping from reported column metadata (argument 4) to Exposed column definitions;
    // The first 3 arguments of helper functions are the expected generated Exposed Table method name,
    // the Kotlin type of the new property, and any optional method args; like `decimal("COL", 10, 2)`.

    @Test
    fun testNumericSqlTypes() {
        assertDefinition("byte", Byte::class.asTypeName(), "", column(Types.TINYINT, "TINYINT"))
        assertDefinition("short", Short::class.asTypeName(), "", column(Types.SMALLINT, "SMALLINT"))
        assertDefinition("integer", Int::class.asTypeName(), "", column(Types.INTEGER, "INT"))
        assertDefinition("integer", Int::class.asTypeName(), "", column(Types.INTEGER, "INTEGER"))
        assertDefinition("long", Long::class.asTypeName(), "", column(Types.BIGINT, "BIGINT"))
        assertDefinition("float", Float::class.asTypeName(), "", column(Types.REAL, "REAL"))
        assertDefinition("float", Float::class.asTypeName(), "", column(Types.DOUBLE, "FLOAT"))
        assertDefinition("double", Double::class.asTypeName(), "", column(Types.DOUBLE, "DOUBLE PRECISION"))
        assertDefinition("decimal", BigDecimal::class.asTypeName(), ", 10, 2", column(Types.NUMERIC, "NUMERIC(10, 2)", size = 10, scale = 2))
        assertDefinition("decimal", BigDecimal::class.asTypeName(), ", 10, 2", column(Types.DECIMAL, "DECIMAL(10, 2)", size = 10, scale = 2))
        assertDefinition("decimal", BigDecimal::class.asTypeName(), ", 5, 0", column(Types.NUMERIC, "NUMERIC(5)", size = 5, scale = null))
        assertDefinition("decimal", BigDecimal::class.asTypeName(), ", 7, 3", column(Types.NUMERIC, "NUMERIC", size = 7, scale = 3))
        assertNull(column(Types.NUMERIC, "NUMERIC", size = null).toColumnDefinition())
    }

    @Test
    fun testCharacterSqlTypes() {
        assertDefinition("char", Char::class.asTypeName(), "", column(Types.CHAR, "CHAR", size = 1))
        assertDefinition("char", String::class.asTypeName(), ", 3", column(Types.CHAR, "CHAR(3)", size = 3))
        assertDefinition("varchar", String::class.asTypeName(), ", 255", column(Types.VARCHAR, "VARCHAR(255)", size = 255))
        assertDefinition("text", String::class.asTypeName(), "", column(Types.VARCHAR, "VARCHAR", size = 1_000_000_000))
        assertDefinition("text", String::class.asTypeName(), "", column(Types.CLOB, "CLOB", size = 1_000_000_000))
        assertDefinition("text", String::class.asTypeName(), "", column(Types.LONGVARCHAR, "TEXT"))
    }

    @Test
    fun testBinarySqlTypes() {
        assertDefinition("binary", ByteArray::class.asTypeName(), ", 16", column(Types.VARBINARY, "VARBINARY(16)", size = 16))
        assertDefinition("binary", ByteArray::class.asTypeName(), ", 16", column(Types.BINARY, "BINARY(16)", size = 16))
        assertDefinition("binary", ByteArray::class.asTypeName(), "", column(Types.VARBINARY, "VARBINARY", size = 1_000_000_000))
        assertDefinition("blob", ExposedBlob::class.asTypeName(), "", column(Types.BLOB, "BLOB", size = 1_000_000_000))
    }

    @Test
    fun testOtherSqlTypes() {
        assertDefinition("bool", Boolean::class.asTypeName(), "", column(Types.BOOLEAN, "BOOLEAN"))
        assertDefinition("bool", Boolean::class.asTypeName(), "", column(Types.BIT, "BIT"))
        assertDefinition("uuid", UUID_TYPE_NAME, "", column(Types.BINARY, "UUID", size = 16))
        assertDefinition("uuid", UUID_TYPE_NAME, "", column(Types.OTHER, "uuid", size = 16))
    }

    @Test
    fun testJdbcTypeFallbackWithoutSqlType() {
        assertDefinition("byte", Byte::class.asTypeName(), "", column(Types.TINYINT, ""))
        assertDefinition("short", Short::class.asTypeName(), "", column(Types.SMALLINT, ""))
        assertDefinition("integer", Int::class.asTypeName(), "", column(Types.INTEGER, ""))
        assertDefinition("long", Long::class.asTypeName(), "", column(Types.BIGINT, ""))
        assertDefinition("float", Float::class.asTypeName(), "", column(Types.REAL, ""))
        assertDefinition("float", Float::class.asTypeName(), "", column(Types.FLOAT, ""))
        assertDefinition("double", Double::class.asTypeName(), "", column(Types.DOUBLE, ""))
        assertDefinition("decimal", BigDecimal::class.asTypeName(), ", 10, 2", column(Types.DECIMAL, "", size = 10, scale = 2))
        assertDefinition("bool", Boolean::class.asTypeName(), "", column(Types.BOOLEAN, ""))
        assertDefinition("char", String::class.asTypeName(), ", 3", column(Types.CHAR, "", size = 3))
        assertDefinition("varchar", String::class.asTypeName(), ", 255", column(Types.VARCHAR, "", size = 255))
        assertDefinition("text", String::class.asTypeName(), "", column(Types.VARCHAR, "", size = 1_000_000_000))
        assertDefinition("text", String::class.asTypeName(), "", column(Types.VARCHAR, "", size = null))
        assertDefinition("text", String::class.asTypeName(), "", column(Types.CLOB, ""))
        assertDefinition("binary", ByteArray::class.asTypeName(), ", 16", column(Types.VARBINARY, "", size = 16))
        assertDefinition("binary", ByteArray::class.asTypeName(), "", column(Types.LONGVARBINARY, "", size = null))
        assertDefinition("blob", ExposedBlob::class.asTypeName(), "", column(Types.BLOB, ""))
    }

    @Test
    fun testUuidColumnTypeSelectsFactoryFunction() {
        val column = column(Types.BINARY, "UUID", size = 16)

        val kotlinDefinition = assertNotNull(column.toColumnDefinition(UuidColumnType.KOTLIN))
        assertEquals("uuid", kotlinDefinition.function)
        assertEquals(UUID_TYPE_NAME, kotlinDefinition.typeName)
        assertFalse(kotlinDefinition.isExtensionFunction)

        val javaDefinition = assertNotNull(column.toColumnDefinition(UuidColumnType.JAVA))
        assertEquals("javaUUID", javaDefinition.function)
        assertEquals(JAVA_UUID_TYPE_NAME, javaDefinition.typeName)
        assertTrue(javaDefinition.isExtensionFunction)
        assertEquals("org.jetbrains.exposed.v1.core.java.javaUUID", javaDefinition.memberName.toString())
    }

    @Test
    fun testTemporalSqlTypesDefaultToKotlinDatetime() {
        val functions = "org.jetbrains.exposed.v1.datetime"
        assertTemporal("date", ClassName("kotlinx.datetime", "LocalDate"), functions, column(Types.DATE, "DATE"))
        assertTemporal("time", ClassName("kotlinx.datetime", "LocalTime"), functions, column(Types.TIME, "TIME"))
        assertTemporal("time", ClassName("kotlinx.datetime", "LocalTime"), functions, column(Types.TIME, "TIME(9)"))
        assertTemporal("datetime", ClassName("kotlinx.datetime", "LocalDateTime"), functions, column(Types.TIMESTAMP, "TIMESTAMP"))
        assertTemporal("datetime", ClassName("kotlinx.datetime", "LocalDateTime"), functions, column(Types.TIMESTAMP, "TIMESTAMP(9)"))
        assertTemporal("datetime", ClassName("kotlinx.datetime", "LocalDateTime"), functions, column(Types.TIMESTAMP, "DATETIME"))
        assertTemporal(
            "timestampWithTimeZone",
            ClassName("java.time", "OffsetDateTime"),
            functions,
            column(Types.TIMESTAMP_WITH_TIMEZONE, "TIMESTAMP(9) WITH TIME ZONE")
        )
    }

    @Test
    fun testTemporalSqlTypesUseConfiguredModule() {
        val javaTime = DateTimeColumnType.JAVA_TIME
        val javaFunctions = "org.jetbrains.exposed.v1.javatime"
        assertTemporal("date", ClassName("java.time", "LocalDate"), javaFunctions, column(Types.DATE, "DATE"), javaTime)
        assertTemporal("time", ClassName("java.time", "LocalTime"), javaFunctions, column(Types.TIME, "TIME"), javaTime)
        assertTemporal("datetime", ClassName("java.time", "LocalDateTime"), javaFunctions, column(Types.TIMESTAMP, "TIMESTAMP(9)"), javaTime)
        assertTemporal(
            "timestampWithTimeZone",
            ClassName("java.time", "OffsetDateTime"),
            javaFunctions,
            column(Types.TIMESTAMP_WITH_TIMEZONE, "TIMESTAMP(9) WITH TIME ZONE"),
            javaTime
        )

        val jodaTime = DateTimeColumnType.JODA_TIME
        val jodaFunctions = "org.jetbrains.exposed.v1.jodatime"
        assertTemporal("date", ClassName("org.joda.time", "DateTime"), jodaFunctions, column(Types.DATE, "DATE"), jodaTime)
        assertTemporal("time", ClassName("org.joda.time", "LocalTime"), jodaFunctions, column(Types.TIME, "TIME"), jodaTime)
        assertTemporal("datetime", ClassName("org.joda.time", "DateTime"), jodaFunctions, column(Types.TIMESTAMP, "TIMESTAMP(9)"), jodaTime)
        assertTemporal(
            "timestampWithTimeZone",
            ClassName("org.joda.time", "DateTime"),
            jodaFunctions,
            column(Types.TIMESTAMP_WITH_TIMEZONE, "TIMESTAMP(9) WITH TIME ZONE"),
            jodaTime
        )
    }

    @Test
    fun testTemporalJdbcTypeFallbackWithoutSqlType() {
        val functions = "org.jetbrains.exposed.v1.datetime"
        assertTemporal("date", ClassName("kotlinx.datetime", "LocalDate"), functions, column(Types.DATE, ""))
        assertTemporal("time", ClassName("kotlinx.datetime", "LocalTime"), functions, column(Types.TIME, ""))
        assertTemporal("datetime", ClassName("kotlinx.datetime", "LocalDateTime"), functions, column(Types.TIMESTAMP, ""))
        assertTemporal("timestampWithTimeZone", ClassName("java.time", "OffsetDateTime"), functions, column(Types.TIMESTAMP_WITH_TIMEZONE, ""))
        assertNull(column(Types.TIME_WITH_TIMEZONE, "").toColumnDefinition())
    }

    @Test
    fun testJsonSqlTypeDefaultsToStringWithIdentitySerializers() {
        val definition = assertNotNull(column(Types.OTHER, "JSON").toColumnDefinition())

        assertEquals("json", definition.function)
        assertEquals(String::class.asTypeName(), definition.typeName)
        assertEquals(String::class.asTypeName(), definition.typeArgument)
        assertEquals(", { it }, { it }", definition.arguments.toString())
        assertTrue(definition.isExtensionFunction)
        assertEquals("org.jetbrains.exposed.v1.json.json", definition.memberName.toString())
        assertEquals(ExposedModule.JSON, definition.requiredModule)

        val call = definition.factoryCall("DOC").toString()
        assertTrue(call.startsWith("json<") || call.startsWith("org.jetbrains.exposed.v1.json.json<"), "Unexpected call: $call")
        assertTrue(call.endsWith("String>(\"DOC\", { it }, { it })"), "Unexpected call: $call")
        assertTrue(definition.mappingLog.orEmpty().contains("jsonb()"), "The note must mention the jsonb() substitution")
    }

    @Test
    fun testArraySqlTypesUseExplicitElementColumnTypes() {
        assertArray("IntegerColumnType()", Int::class.asTypeName(), column(Types.ARRAY, "INT ARRAY"))
        assertArray("IntegerColumnType(), 10", Int::class.asTypeName(), column(Types.ARRAY, "INT ARRAY[10]"))
        assertArray("LongColumnType()", Long::class.asTypeName(), column(Types.ARRAY, "BIGINT ARRAY"))
        assertArray("BooleanColumnType()", Boolean::class.asTypeName(), column(Types.ARRAY, "BOOLEAN ARRAY"))
        assertArray("DecimalColumnType(10, 2)", BigDecimal::class.asTypeName(), column(Types.ARRAY, "NUMERIC(10, 2) ARRAY"))
        assertArray("VarCharColumnType(255), 10", String::class.asTypeName(), column(Types.ARRAY, "VARCHAR(255) ARRAY[10]"))
        assertArray("TextColumnType()", String::class.asTypeName(), column(Types.ARRAY, "VARCHAR ARRAY"))
        assertArray("TextColumnType()", String::class.asTypeName(), column(Types.ARRAY, "CLOB ARRAY"))
        assertArray("CharacterColumnType()", Char::class.asTypeName(), column(Types.ARRAY, "CHAR ARRAY"))
        assertArray("CharColumnType(3)", String::class.asTypeName(), column(Types.ARRAY, "CHAR(3) ARRAY"))
        assertArray("BinaryColumnType(16)", ByteArray::class.asTypeName(), column(Types.ARRAY, "VARBINARY(16) ARRAY"))
        assertArray("UuidColumnType()", UUID_TYPE_NAME, column(Types.ARRAY, "UUID ARRAY"))
        assertArray("UUIDColumnType()", JAVA_UUID_TYPE_NAME, column(Types.ARRAY, "UUID ARRAY"), uuidColumnType = UuidColumnType.JAVA)
        assertArray("KotlinLocalDateColumnType()", ClassName("kotlinx.datetime", "LocalDate"), column(Types.ARRAY, "DATE ARRAY"))
        assertArray(
            "KotlinOffsetDateTimeColumnType()",
            ClassName("java.time", "OffsetDateTime"),
            column(Types.ARRAY, "TIMESTAMP(9) WITH TIME ZONE ARRAY")
        )
        assertArray(
            "JavaLocalDateTimeColumnType()",
            ClassName("java.time", "LocalDateTime"),
            column(Types.ARRAY, "TIMESTAMP(9) ARRAY"),
            dateTimeColumnType = DateTimeColumnType.JAVA_TIME
        )
        assertArray(
            "DateTimeWithTimeZoneColumnType()",
            ClassName("org.joda.time", "DateTime"),
            column(Types.ARRAY, "TIMESTAMP(9) WITH TIME ZONE ARRAY"),
            dateTimeColumnType = DateTimeColumnType.JODA_TIME
        )

        assertNull(column(Types.ARRAY, "JSON ARRAY").toColumnDefinition())
        assertNull(column(Types.ARRAY, "INTERVAL DAY ARRAY").toColumnDefinition())
        assertNull(column(Types.ARRAY, "TIME(0) WITH TIME ZONE ARRAY").toColumnDefinition())
    }

    @Test
    fun testEnumerationSqlTypesBecomeCustomEnumerationsWithNestedEnumClasses() {
        val definition = assertNotNull(column(Types.OTHER, "ENUM('Bar', 'Baz')").toColumnDefinition())

        assertEquals("customEnumeration", definition.function)
        assertFalse(definition.isExtensionFunction, "customEnumeration() is a Table member")
        assertEquals(ClassName("", "Col"), definition.typeName)
        val arguments = definition.arguments.toString()
        assertTrue(arguments.startsWith(", \"ENUM('Bar', 'Baz')\", { "), "The literal must be passed verbatim: $arguments")
        assertTrue(arguments.endsWith(".valueOf(it as String) }, { it.name }"), "Unexpected arguments: $arguments")
        val enumType = assertNotNull(definition.nestedType)
        assertEquals("Col", enumType.name)
        assertTrue(KModifier.INTERNAL in enumType.modifiers, "The generated enum class must be internal")
        assertEquals(listOf("Bar", "Baz"), enumType.enumConstants.keys.toList())
        assertTrue(enumType.kdoc.toString().contains("customEnumeration property [col]"), "Unexpected KDoc: ${enumType.kdoc}")
        assertTrue(definition.mappingLog.orEmpty().contains("nested enum class Col"), "Unexpected note: ${definition.mappingLog}")
        assertFalse(definition.supportsSequenceAutoIncrement)

        val odd = assertNotNull(column(Types.OTHER, "ENUM('a b', 'c''d', 'IN')").toColumnDefinition())
        assertEquals(listOf("a b", "c'd", "IN"), assertNotNull(odd.nestedType).enumConstants.keys.toList())
        assertTrue(odd.arguments.toString().startsWith(", \"ENUM('a b', 'c''d', 'IN')\", "))

        assertNull(column(Types.OTHER, "ENUM('x.y', 'z')").toColumnDefinition())
        assertNull(column(Types.OTHER, "ENUM('')").toColumnDefinition())
        assertNull(column(Types.OTHER, "ENUM()").toColumnDefinition())
    }

    @Test
    fun testUnsupportedTypesAreNotMapped() {
        assertNull(column(Types.TIME_WITH_TIMEZONE, "TIME(0) WITH TIME ZONE").toColumnDefinition())
        assertNull(column(Types.OTHER, "INTERVAL DAY").toColumnDefinition())
        assertNull(column(Types.ARRAY, "").toColumnDefinition())
        assertNull(column(Types.OTHER, "GEOMETRY").toColumnDefinition())
        assertNull(column(Types.JAVA_OBJECT, "OBJECT").toColumnDefinition())
        assertNull(column(Types.OTHER, "").toColumnDefinition())
    }

    private fun assertArray(
        expectedGenerated: String,
        elementTypeName: TypeName,
        column: ColumnMetadata,
        uuidColumnType: UuidColumnType = UuidColumnType.KOTLIN,
        dateTimeColumnType: DateTimeColumnType = DateTimeColumnType.KOTLIN_DATETIME,
    ) {
        val definition = assertNotNull(column.toColumnDefinition(uuidColumnType, dateTimeColumnType), "Expected $column to be mapped")
        assertEquals("array", definition.function)
        assertFalse(definition.isExtensionFunction, "array() is a Table member")
        assertEquals(ClassName("kotlin.collections", "List").parameterizedBy(elementTypeName), definition.typeName)
        val arguments = definition.arguments.toString()
        assertTrue(arguments.endsWith(expectedGenerated), "Expected arguments ending with '$expectedGenerated' for $column, got: $arguments")
        assertFalse(definition.supportsSequenceAutoIncrement)
    }

    private fun assertTemporal(
        function: String,
        typeName: TypeName,
        functionPackage: String,
        column: ColumnMetadata,
        dateTimeColumnType: DateTimeColumnType = DateTimeColumnType.KOTLIN_DATETIME,
    ) {
        val definition = assertNotNull(column.toColumnDefinition(dateTimeColumnType = dateTimeColumnType), "Expected $column to be mapped")
        assertEquals(function, definition.function)
        assertEquals(typeName, definition.typeName)
        assertEquals("", definition.arguments.toString())
        assertTrue(definition.isExtensionFunction)
        assertEquals("$functionPackage.$function", definition.memberName.toString())
    }

    private fun assertDefinition(function: String, typeName: TypeName, arguments: String, column: ColumnMetadata) {
        val definition = assertNotNull(column.toColumnDefinition(), "Expected $column to be mapped")
        assertEquals(function, definition.function)
        assertEquals(typeName, definition.typeName)
        assertEquals(arguments, definition.arguments.toString())
    }

    private fun ColumnMetadata.toColumnDefinition(
        uuidColumnType: UuidColumnType = UuidColumnType.KOTLIN,
        dateTimeColumnType: DateTimeColumnType = DateTimeColumnType.KOTLIN_DATETIME,
    ): ColumnDefinition? = toColumnDefinition(H2Syntax, uuidColumnType, dateTimeColumnType)
}

internal fun column(jdbcType: Int, sqlType: String, size: Int? = null, scale: Int? = null): ColumnMetadata = ColumnMetadata(
    name = "COL",
    jdbcType = jdbcType,
    sqlType = sqlType,
    nullable = false,
    size = size,
    scale = scale,
    autoIncrement = false,
    defaultDbValue = null,
)
