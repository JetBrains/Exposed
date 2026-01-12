package org.jetbrains.exposed.v1.migration.r2dbc

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.datetime.*
import org.jetbrains.exposed.v1.json.json
import org.jetbrains.exposed.v1.json.jsonb
import org.jetbrains.exposed.v1.money.CurrencyColumnType
import org.jetbrains.exposed.v1.money.currency
import org.jetbrains.exposed.v1.r2dbc.tests.currentDialectTest
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import java.util.UUID as JavaUUID

object MigrationTestsData {
    object TableWithoutAutoIncrement : IdTable<Long>("test_table") {
        override val id: Column<EntityID<Long>> = long("id").entityId()
    }

    object TableWithAutoIncrement : IdTable<Long>("test_table") {
        override val id: Column<EntityID<Long>> = long("id").autoIncrement().entityId()
    }

    object TableWithAutoIncrementCustomSequence : IdTable<Long>("test_table") {
        override val id: Column<EntityID<Long>> = long("id").autoIncrement(customSequence).entityId()
    }

    object TableWithAutoIncrementSequenceName : IdTable<Long>("test_table") {
        override val id: Column<EntityID<Long>> = long("id").autoIncrement(SEQUENCE_NAME).entityId()
    }

    const val SEQUENCE_NAME = "custom_sequence"

    /**
     * This Sequence constructor calls `currentDialectTest`, so it must only be used inside a transaction.
     *
     * Any attempt to use it in a table outside of this object requires wrapping the table in a lazy block, at minimum.
     */
    val customSequence = Sequence(
        name = "my_sequence",
        startWith = 1,
        minValue = 1,
        maxValue = currentDialectTest.sequenceMaxValue
    )

    object ColumnTypesTester : Table("tester") {
        val byte = byte("byte_col")
        val ubyte = ubyte("ubyte_col")
        val short = short("short_col")
        val ushort = ushort("ushort_col")
        val integer = integer("integer_col")
        val uinteger = uinteger("uinteger_col")
        val long = long("long_col")
        val ulong = ulong("ulong_col")
        val float = float("float_col")
        val double = double("double_col")
        val decimal = decimal("decimal_col", 6, 2)
        val decimal2 = decimal("decimal_col_2", 3, 2)
        val char = char("char_col")
        val letter = char("letter_col", 1)
        val char2 = char("char_col_2", 2)
        val varchar = varchar("varchar_col", 14)
        val varchar2 = varchar("varchar_col_2", 28)
        val text = text("text_col")
        val mediumText = mediumText("mediumText_col")
        val largeText = largeText("largeText_col")

        val binary = binary("binary_col", 123)
        val binary2 = binary("binary_col_2", 456)
        val blob = blob("blob_col")

        @OptIn(ExperimentalUuidApi::class)
        val uuid = uuid("uuid_col")
        val javaUUID = javaUUID("java_uuid_col")
        val bool = bool("boolean_col")
        val enum1 = enumeration("enum_col_1", TestEnum::class)
        val enum2 = enumeration<TestEnum>("enum_col_2")
        val enum3 = enumerationByName("enum_col_3", 25, TestEnum::class)
        val enum4 = enumerationByName("enum_col_4", 64, TestEnum::class)
        val enum5 = enumerationByName<TestEnum>("enum_col_5", 16)
        val enum6 = enumerationByName<TestEnum>("enum_col_6", 32)
        // customEnumeration column removed - see JDBC for original

        val currency = currency("currency_col")
        val date = date("date_col")
        val datetime = datetime("datetime_col")
        val time = time("time_col")

        @OptIn(ExperimentalTime::class)
        val timestamp = timestamp("timestamp_col")
        val xTimestamp = xTimestamp("x_timestamp_col")
        val timestampWithTimeZone = timestampWithTimeZone("timestampWithTimeZone_col")
        val duration = duration("duration_col")
        val intArrayJson = json<IntArray>("json_col", Json.Default)
        val intArrayJsonb = jsonb<IntArray>("jsonb_col", Json.Default)
    }

    enum class TestEnum { A, B, C }

    object ArraysTester : Table("tester") {
        val byteArray = array("byteArray", ByteColumnType())
        val ubyteArray = array("ubyteArray", UByteColumnType())
        val shortArray = array("shortArray", ShortColumnType(), 10)
        val ushortArray = array("ushortArray", UShortColumnType(), 10)
        val intArray = array<Int>("intArray", 20)
        val uintArray = array<UInt>("uintArray", 20)
        val longArray = array<Long>("longArray", 30)
        val ulongArray = array<ULong>("ulongArray", 30)
        val floatArray = array<Float>("floatArray", 40)
        val doubleArray = array<Double>("doubleArray", 50)
        val decimalArray = array("decimalArray", DecimalColumnType(6, 3), 60)
        val charArray = array("charArray", CharacterColumnType(), 70)
        val initialsArray = array("initialsArray", CharColumnType(2), 45)
        val varcharArray = array("varcharArray", VarCharColumnType(), 80)
        val textArray = array("textArray", TextColumnType(), 90)
        val mediumTextArray = array("mediumTextArray", MediumTextColumnType(), 100)
        val largeTextArray = array("largeTextArray", LargeTextColumnType(), 110)
        val binaryArray = array("binaryArray", BinaryColumnType(123), 120)
        val blobArray = array("blobArray", BlobColumnType(), 130)

        @OptIn(ExperimentalUuidApi::class)
        val uuidArray = array<Uuid>("uuidArray", 140)
        val javaUUIDArray = array<JavaUUID>("javaUuidArray", 140)
        val booleanArray = array<Boolean>("booleanArray", 150)
        val currencyArray = array("currencyArray", CurrencyColumnType(), 25)
        val dateArray = array("dateArray", KotlinLocalDateColumnType(), 366)
        val datetimeArray = array("datetimeArray", KotlinLocalDateTimeColumnType(), 10)
        val timeArray = array("timeArray", KotlinLocalTimeColumnType(), 14)

        @OptIn(ExperimentalTime::class)
        val timestampArray = array("timestampArray", KotlinInstantColumnType(), 10)
        val xTimestampArray = array("xTimestampArray", XKotlinInstantColumnType(), 10)
        val timestampWithTimeZoneArray = array("timestampWithTimeZoneArray", KotlinOffsetDateTimeColumnType(), 10)
        val durationArray = array("durationArray", KotlinDurationColumnType(), 7)
    }
}
