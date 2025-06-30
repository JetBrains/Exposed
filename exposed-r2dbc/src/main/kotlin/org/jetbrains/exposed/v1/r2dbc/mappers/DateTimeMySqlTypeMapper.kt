package org.jetbrains.exposed.v1.r2dbc.mappers

import io.r2dbc.spi.Row
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.vendors.DatabaseDialect
import org.jetbrains.exposed.v1.core.vendors.MariaDBDialect
import org.jetbrains.exposed.v1.core.vendors.MysqlDialect
import java.sql.Timestamp
import java.time.Instant

class DateTimeMySqlTypeMapper : TypeMapper {
    @Suppress("MagicNumber")
    override val priority = 0.25

    override val dialects = listOf(MysqlDialect::class)

    @Suppress("UNCHECKED_CAST")
    override fun <T> getValue(
        row: Row,
        type: Class<T>?,
        index: Int,
        dialect: DatabaseDialect,
        columnType: IColumnType<*>
    ): ValueContainer<T?> {
        if (dialect is MariaDBDialect) {
            return NoValueContainer()
        }

        return when (type) {
            // It is tricky, probably the reason for MySql special case is not here but in `KotlinInstantColumnType`
            // The problem is that the line `rs.getObject(index, java.sql.Timestamp::class.java)` in method `valueFromDB()` inside
            // the column type changes the time according to the time zone, and reverts it back in `valueFromDB`
            // But for R2DBC it does not happen. This line changes that behaviour to match it to JDBC behaviour.
            Timestamp::class.java -> PresentValueContainer(
                row.get(index - 1, Instant::class.java)?.let { Timestamp.from(it) as T }
            )

            else -> NoValueContainer()
        }
    }
}
