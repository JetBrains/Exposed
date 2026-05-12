package org.jetbrains.exposed.v1.r2dbc.mappers

import io.r2dbc.spi.Statement
import org.jetbrains.exposed.v1.core.BasicVectorColumnType
import org.jetbrains.exposed.v1.core.FloatVectorColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntVectorColumnType
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.MariaDBDialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import kotlin.reflect.KClass

/**
 * Mapper for vector data types.
 */
class VectorTypeMapper : TypeMapper {
    @Suppress("MagicNumber")
    override val priority = 0.2

    // OracleDialect should rely on its own handler, to avoid duplicating logic here
    override val dialects: List<KClass<out DatabaseDialect>>
        get() = listOf(PostgreSQLDialect::class, MysqlDialect::class)

    override val columnTypes: List<KClass<out IColumnType<*>>>
        get() = listOf(BasicVectorColumnType::class)

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        typeMapping: R2dbcTypeMapping,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        if (columnType !is BasicVectorColumnType) return false

        if (value == null && dialect is MariaDBDialect) {
            statement.bindNull(index - 1, String::class.java)
            return true
        }
        if (value == null && columnType is FloatVectorColumnType) {
            statement.bindNull(index - 1, Array<Float>::class.java)
            return true
        }
        if (value == null && columnType is IntVectorColumnType) {
            statement.bindNull(index - 1, Array<Int>::class.java)
            return true
        }

        when (value) {
            is FloatArray -> {
                statement.bind(index - 1, value.toTypedArray())
                return true
            }
            is IntArray -> {
                statement.bind(index - 1, value.toTypedArray())
                return true
            }
            is ByteArray -> {
                statement.bind(index - 1, value)
                return true
            }
        }

        return false
    }
}
