package org.jetbrains.exposed.r2dbc.sql.statements

import io.r2dbc.spi.Connection
import io.r2dbc.spi.Parameters
import io.r2dbc.spi.R2dbcType
import io.r2dbc.spi.Statement
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcPreparedStatementApi
import org.jetbrains.exposed.r2dbc.sql.statements.api.R2dbcResult
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.vendors.DatabaseDialect
import org.jetbrains.exposed.sql.vendors.PostgreSQLDialect
import java.io.InputStream
import java.math.BigDecimal
import java.time.Duration
import java.util.*

/**
 * Class representing a precompiled SQL [Statement] from the R2DBC SPI.
 *
 * The result row generated by executing this statement contains auto-generated keys based on the value of
 * [wasGeneratedKeysRequested].
 */
class R2dbcPreparedStatementImpl(
    private val statement: Statement,
    // the property below is only here for setTimeout() --> should this logic be in R2dbcConnectionImpl instead
    val connection: Connection,
    val wasGeneratedKeysRequested: Boolean,
    private val currentDialect: DatabaseDialect
) : R2dbcPreparedStatementApi {
    private var resultRow: R2dbcResult? = null

    override suspend fun getResultRow(): R2dbcResult? {
        if (resultRow == null && wasGeneratedKeysRequested) {
            val resultPublisher = statement.execute()
            resultRow = R2dbcResult(resultPublisher)
        }

        return resultRow
    }

    override suspend fun setFetchSize(value: Int?) {
        value?.let { statement.fetchSize(value) }
    }

    override suspend fun setTimeout(value: Int?) {
        value?.let { connection.setStatementTimeout(Duration.ofSeconds(value.toLong())) }
    }

    override suspend fun addBatch() {
        statement.add()
    }

    override suspend fun executeQuery(): R2dbcResult = R2dbcResult(statement.execute())

    override suspend fun executeUpdate(): Int {
        val result = statement.execute()
        val r2dbcResult = R2dbcResult(result)
        resultRow = r2dbcResult

        // Todo discuss if a return value is even necessary (since never used)
        return 0
    }

    override fun set(index: Int, value: Any) {
        val convertedValue = when (value) {
            is java.sql.Timestamp -> value.toLocalDateTime()
            else -> value
        }
        statement.bind(index - 1, convertedValue)
    }

    override fun setNull(index: Int, columnType: IColumnType<*>) {
        val columnValueType = when (columnType) {
            is EntityIDColumnType<*> -> {
                setNull(index, columnType.idColumn.columnType)
                return
            }
            is ColumnWithTransform<*, *> -> {
                setNull(index, columnType.delegate)
                return
            }
            is ArrayColumnType<*, *> -> columnType.arrayDeclaration()
            is ByteColumnType -> Byte::class.java
            is UByteColumnType -> UByte::class.java
            is ShortColumnType -> Short::class.java
            is UShortColumnType -> UShort::class.java
            is IntegerColumnType -> Integer::class.java
            is UIntegerColumnType -> UInt::class.java
            is LongColumnType -> Long::class.java
            is ULongColumnType -> ULong::class.java
            is FloatColumnType -> Float::class.java
            is DoubleColumnType -> Double::class.java
            is DecimalColumnType -> BigDecimal::class.java
            is BasicBinaryColumnType, is BlobColumnType -> ByteArray::class.java
            is UUIDColumnType -> UUID::class.java
            is CharacterColumnType -> Char::class.java
            is BooleanColumnType -> Boolean::class.java
            else -> String::class.java
        }
        statement.bindNull(index - 1, columnValueType)
    }

    override fun setInputStream(index: Int, inputStream: InputStream, setAsBlobObject: Boolean) {
        if (setAsBlobObject) {
            statement.bind(index - 1, Parameters.`in`(R2dbcType.BLOB, inputStream.readBytes()))
        } else {
            statement.bind(index - 1, Parameters.`in`(R2dbcType.VARBINARY, inputStream.readBytes()))
        }
    }

    override fun setArray(index: Int, arrayType: ArrayColumnType<*, *>, array: Array<*>) {
        val value = if (currentDialect is PostgreSQLDialect) {
            val list = array.toList()
            when (arrayType.delegate) {
                is BooleanColumnType -> (list as List<Boolean>).toTypedArray()
                is ByteColumnType -> (list as List<Byte>).toTypedArray()
                is UByteColumnType -> (list as List<UByte>).toTypedArray()
                is ShortColumnType -> (list as List<Short>).toTypedArray()
                is UShortColumnType -> (list as List<UShort>).toTypedArray()
                is IntegerColumnType -> (list as List<Int>).toTypedArray()
                is UIntegerColumnType -> (list as List<UInt>).toTypedArray()
                is LongColumnType -> (list as List<Long>).toTypedArray()
                is ULongColumnType -> (list as List<ULong>).toTypedArray()
                is FloatColumnType -> (list as List<Float>).toTypedArray()
                is DoubleColumnType -> (list as List<Double>).toTypedArray()
                is BinaryColumnType -> (list as List<ByteArray>).toTypedArray()
                is TextColumnType -> (list as List<String>).toTypedArray()
                is DecimalColumnType -> (list as List<BigDecimal>).toTypedArray()
                else -> error("Unsupported array type: $arrayType:${arrayType::class}")
            }
        } else {
            Parameters.`in`(R2dbcType.COLLECTION, array)
        }

        statement.bind(index - 1, value)
    }

    override suspend fun closeIfPossible() {
        // do nothing
    }

    override suspend fun executeBatch(): List<Int> {
        val result = statement.execute()
        val r2dbcResult = R2dbcResult(result)

        return if (wasGeneratedKeysRequested) {
            resultRow = r2dbcResult
            emptyList()
        } else {
            resultRow = null
            r2dbcResult.rowsUpdated().toList()
        }
    }

    override suspend fun cancel() {
        // do nothing
    }

    private fun ArrayColumnType<*, *>.arrayDeclaration(): Class<out Array<out Any>> = when (delegate) {
        is ByteColumnType -> Array<Byte>::class.java
        is UByteColumnType -> Array<UByte>::class.java
        is ShortColumnType -> Array<Short>::class.java
        is UShortColumnType -> Array<UShort>::class.java
        is IntegerColumnType -> Array<Integer>::class.java
        is UIntegerColumnType -> Array<UInt>::class.java
        is LongColumnType -> Array<Long>::class.java
        is ULongColumnType -> Array<ULong>::class.java
        is FloatColumnType -> Array<Float>::class.java
        is DoubleColumnType -> Array<Double>::class.java
        is DecimalColumnType -> Array<BigDecimal>::class.java
        is BasicBinaryColumnType, is BlobColumnType -> Array<ByteArray>::class.java
        is UUIDColumnType -> Array<UUID>::class.java
        is CharacterColumnType -> Array<Char>::class.java
        is BooleanColumnType -> Array<Boolean>::class.java
        else -> Array<Any>::class.java
    }
}
