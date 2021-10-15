import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.jetbrains.exposed.sql.statements.jdbc.JdbcPreparedStatementImpl
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.TestDB
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.jetbrains.exposed.sql.tests.shared.assertTrue
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.sql.ResultSet
import java.util.*

class UUIDBackedByLongColumnType(val isAutoIncInt: Boolean) : ColumnType(false) {
    companion object {
        val UPPER_PART = BigInteger.ONE.shiftLeft(64) // 2^64
        val MAX_LONG_VALUE = BigInteger.valueOf(Long.MAX_VALUE)

        fun convertToBigInteger(id: UUID): BigInteger {
            var lo = BigInteger.valueOf(id.leastSignificantBits)
            var hi = BigInteger.valueOf(id.mostSignificantBits)

            // If any of lo/hi parts is negative interpret as unsigned
            if (hi.signum() < 0) hi = hi.add(UPPER_PART)
            if (lo.signum() < 0) lo = lo.add(UPPER_PART)
            return lo.add(hi.multiply(UPPER_PART))
        }

        fun convertFromBigInteger(x: BigInteger): UUID {
            val parts = x.divideAndRemainder(UPPER_PART)
            var hi = parts[0]
            var lo = parts[1]
            if (MAX_LONG_VALUE < lo) lo = lo.subtract(UPPER_PART)
            if (MAX_LONG_VALUE < hi) hi = hi.subtract(UPPER_PART)
            return UUID(hi.longValueExact(), lo.longValueExact())
        }
    }

    override fun sqlType(): String = when (isAutoIncInt) {
        true -> TransactionManager.current().db.dialect.dataTypeProvider.integerAutoincType()
        false -> "DECIMAL(50,0) UNSIGNED"
    }

    override fun readObject(rs: ResultSet, index: Int): Any? {
        return rs.getObject(index)
    }

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        if (value is BigInteger) {
            (stmt as JdbcPreparedStatementImpl).statement.setBigDecimal(index, value.toBigDecimal())
//            (stmt as JdbcPreparedStatementImpl).statement.setObject(index, value, Types.BIGINT)
        }
        super.setParameter(stmt, index, value)
    }

    override fun notNullValueToDB(value: Any): Any {
        val uuid = value as UUID
        return convertToBigInteger(uuid)
    }

    override fun valueFromDB(value: Any): Any {
        val bigDecimalRepresentation = when (value) {
            is BigDecimal -> value.toBigInteger()
            is Int -> BigInteger.valueOf(value.toLong())
            else -> error("")
        }
        return convertFromBigInteger(bigDecimalRepresentation)
    }
}

object TestAutoIntTable : Table("testMe") {
    val uuid = registerColumn<UUID>("uuidCol", UUIDBackedByLongColumnType(isAutoIncInt = true))
    override val primaryKey = PrimaryKey(uuid)
}

object TestDecimalTable : Table("testMe2") {
    val uuid = registerColumn<UUID>("uuidCol", UUIDBackedByLongColumnType(isAutoIncInt = false))
    override val primaryKey = PrimaryKey(uuid)
}

class Test : DatabaseTestsBase() {

    @Test
    fun test() {
        withDb(TestDB.MYSQL) {
            try {
                SchemaUtils.create(TestAutoIntTable, TestDecimalTable)
                val uuids = arrayListOf<UUID>()
                repeat(5) {
                    TestAutoIntTable.insert { }
                    TestDecimalTable.insert {
                        it[uuid] = UUID.randomUUID().apply { uuids.add(this) }
                    }
                }

                val uuidIntVal = TestAutoIntTable.selectAll().map { it[TestAutoIntTable.uuid] }
                val uuidDecimalVal = TestDecimalTable.selectAll().map { it[TestDecimalTable.uuid] }
                repeat(5) {
                    assertEquals(uuidIntVal[it], UUID(0, 1L + it))
                    assertTrue(uuids[it] in uuidDecimalVal)
                }
            } finally {
                SchemaUtils.drop(TestAutoIntTable, TestDecimalTable)
            }
        }
    }
}
