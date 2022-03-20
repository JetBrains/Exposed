package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.vendors.db2compat.DB2ResultSet
import java.lang.reflect.Method
import java.sql.PreparedStatement
import java.sql.ResultSet

internal object DB2DataTypeProvider : DataTypeProvider() {
    override fun binaryType(): String {
        exposedLogger.error("The length of the Binary column is missing.")
        error("The length of the Binary column is missing.")
    }

    override fun dateTimeType(): String = "TIMESTAMP"

    override fun ulongType(): String = "BIGINT"

    override fun textType(): String = "VARCHAR(32704)"
}

internal object DB2FunctionProvider : FunctionProvider() {

    override fun random(seed: Int?) = "RAND(${seed?.toString().orEmpty()})"

    override fun queryLimit(size: Int, offset: Long, alreadyOrdered: Boolean): String {
        return (if (offset > 0) " OFFSET $offset ROWS" else "") + " FETCH FIRST $size ROWS ONLY"
    }
}

/**
 * DB2 dialect implementation.
 */
class DB2Dialect : VendorDialect(dialectName, DB2DataTypeProvider, DB2FunctionProvider) {
    override val name: String = dialectName
    override val supportsOnlyIdentifiersInGeneratedKeys: Boolean = true


    companion object {
        /** DB2 dialect name */
        const val dialectName: String = "db2"
    }
}

private val DB2Class by lazy { Class.forName("com.ibm.db2.jcc.DB2PreparedStatement") as Class<out PreparedStatement?> }
val GET_DB_GENERATED_KEYS: Method by lazy { DB2Class.getDeclaredMethod("getDBGeneratedKeys"); }

val PreparedStatement.db2ResultSetCompat: ResultSet
    get() {
        @Suppress("UNCHECKED_CAST")
        return DB2ResultSet((GET_DB_GENERATED_KEYS.invoke(this) as Array<ResultSet>))
    }
