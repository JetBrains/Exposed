package org.jetbrains.exposed.v1.r2dbc.mappers

import io.r2dbc.spi.Statement
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IDateColumnType
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.MariaDBDialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import org.jetbrains.exposed.v1.core.vendors.OracleDialect

/**
 * The mapper that sets the nulls into date/time columns
 */
class DateTimeNullTypeMapper : TypeMapper {

    @Suppress("MagicNumber")
    override val priority = 0.25

    private val classOptions = listOf(
        java.time.LocalDateTime::class.java,
        java.time.LocalTime::class.java,
        java.time.LocalDate::class.java,
    )

    override fun setValue(
        statement: Statement,
        dialect: DatabaseDialect,
        typeMapping: R2dbcTypeMapping,
        columnType: IColumnType<*>,
        value: Any?,
        index: Int
    ): Boolean {
        if (columnType !is IDateColumnType) return false
        if (value != null) return false

        val temporalSupported = dialect is OracleDialect || (dialect is MysqlDialect && dialect !is MariaDBDialect)

        if (temporalSupported) {
            statement.bindNull(index - 1, java.time.temporal.Temporal::class.java)
            return true
        } else {
            for (option in classOptions) {
                try {
                    statement.bindNull(index - 1, option)
                    return true
                } catch (_: RuntimeException) {
                }
            }
            return false
        }
    }
}
