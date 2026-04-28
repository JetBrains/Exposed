package org.jetbrains.exposed.v1.core.vendors

import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.statements.api.RowApi
import org.jetbrains.exposed.v1.core.statements.api.PreparedStatementApi

class OracleVectorColumnType(val dimensions: Int) : ColumnType<FloatArray>() {
    override fun sqlType() = "VECTOR($dimensions, FLOAT32)"

    override fun readObject(rs: RowApi, index: Int): Any? = rs.getObject(index, FloatArray::class.java, this)

    override fun valueFromDB(value: Any): FloatArray =
        when (value) {
            is FloatArray -> value
            else -> {
                // Delegate to readObject which handles oracle.sql.VECTOR
                value.javaClass.getMethod("toFloatArray").invoke(value) as FloatArray
            }
        }

    override fun valueToString(value: FloatArray?): String =
        value?.let { "TO_VECTOR('${it.joinToString(prefix = "[", postfix = "]")}')" } ?: "NULL"

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        when (value) {
            null, is Op.NULL -> stmt.setNull(index, this)
            is FloatArray -> {
                val failure = OracleVectorBinder.tryBind(stmt, index, value, this)
                if (failure != null) {
                    throw IllegalStateException(
                        "Failed to bind Oracle VECTOR parameter at index $index: ${failure.message}",
                        failure
                    )
                }
            }
            else -> super.setParameter(stmt, index, value)
        }
    }
}

private object OracleVectorBinder {
    private val vectorClass by lazy { Class.forName("oracle.sql.VECTOR") }
    private val ofFloat32Values by lazy { vectorClass.getMethod("ofFloat32Values", Any::class.java) }

    @Suppress("TooGenericExceptionCaught")
    fun tryBind(
        stmt: PreparedStatementApi,
        index: Int,
        value: FloatArray,
        columnType: OracleVectorColumnType
    ): Throwable? {
        return try {
            val vectorValue = ofFloat32Values.invoke(null, value)
            stmt.set(index, vectorValue, columnType)
            null
        } catch (t: Throwable) {
            t
        }
    }
}
