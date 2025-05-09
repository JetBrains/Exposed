package org.jetbrains.exposed.v1.r2dbc.sql.mappers

import io.r2dbc.spi.Parameters
import io.r2dbc.spi.R2dbcType
import io.r2dbc.spi.Statement
import org.jetbrains.exposed.v1.core.BasicBinaryColumnType
import org.jetbrains.exposed.v1.core.BlobColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import java.io.InputStream
import kotlin.reflect.KClass

/**
 * Mapper for binary data types.
 */
class BinaryTypeMapper : TypeMapper {
    override val columnTypes: List<KClass<out IColumnType<*>>>
        get() = listOf(
            BasicBinaryColumnType::class,
            BlobColumnType::class
        )

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        mapperRegistry: TypeMapperRegistry,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        if (columnType !is BasicBinaryColumnType && columnType !is BlobColumnType) {
            return false
        }

        if (value == null) {
            statement.bindNull(index - 1, ByteArray::class.java)
            return true
        }

        if (value is InputStream) {
            val type = if (columnType is BlobColumnType) {
                Parameters.`in`(R2dbcType.BLOB, value.readBytes())
            } else {
                Parameters.`in`(R2dbcType.VARBINARY, value.readBytes())
            }

            statement.bind(index - 1, type)
            return true
        }

        if (value is ExposedBlob) {
            if (columnType is BlobColumnType) {
                statement.bind(index - 1, Parameters.`in`(R2dbcType.BLOB, value.bytes))
            } else {
                statement.bind(index - 1, Parameters.`in`(R2dbcType.VARBINARY, value.bytes))
            }
            return true
        }

        if (value is ByteArray) {
            statement.bind(index - 1, value)
            return true
        }

        return false
    }
}
