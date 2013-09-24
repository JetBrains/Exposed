package kotlin.sql

import java.util.ArrayList
import org.joda.time.DateTime
import kotlin.sql.Join.JoinPart
import java.math.BigDecimal
import java.sql.Blob
import kotlin.dao.IdTable

trait FieldSet {
    val fields: List<Field<*>>
    val source: ColumnSet
}

abstract class ColumnSet(): FieldSet {
    abstract val columns: List<Column<*>>
    override val fields: List<Field<*>> get() = columns
    override val source = this

    abstract fun describe(s: Session): String

    fun slice(vararg columns: Field<*>): FieldSet = Slice(this, listOf(*columns))
}

class Slice(override val source: ColumnSet, override val fields: List<Field<*>>): FieldSet

enum class JoinType {
    INNER
    LEFT
    RIGHT
    FULL
}

fun Table.join (otherTable: Table) : Join {
    return Join (this, otherTable, JoinType.INNER)
}

fun Table.join (otherTable: Table, joinType: JoinType) : Join {
    return Join (this, otherTable, joinType)
}

fun Table.innerJoin (otherTable: Table) : Join {
    return Join (this, otherTable, JoinType.INNER)
}

class Join (val table: Table, otherTable: Table, joinType: JoinType = JoinType.INNER) : ColumnSet() {
    class JoinPart (val joinType: JoinType, val table: Table, val pkColumn: Column<*>, val fkColumn: Column<*>) {
    }

    val joinParts: ArrayList<JoinPart> = ArrayList<JoinPart>();

    fun innerJoin (otherTable: Table) : Join {
        return join(otherTable, JoinType.INNER)
    }

    fun join (otherTable: Table, joinType: JoinType = JoinType.INNER) : Join {
        val keysPair = findKeys (this, otherTable) ?: findKeys (otherTable, this)
        if (keysPair == null) throw RuntimeException ("Cannot join with ${otherTable.tableName} as there is no matching primary key/ foreign key pair")

        return join(otherTable, joinType, keysPair.first, keysPair.second)
    }

    fun join(otherTable: Table, joinType: JoinType, onColumn: Column<*>, otherColumn: Column<*>): Join {
        joinParts.add(JoinPart(joinType, otherTable, onColumn, otherColumn))
        return this
    }

    private fun findKeys(a: ColumnSet, b: ColumnSet): Pair<Column<*>, Column<*>>? {
        for (a_pk in a.columns.filter { it is PKColumn<*> }) {
            val b_fk = b.columns.find { it.referee == a_pk }
            if (b_fk != null)
                return a_pk to b_fk!!
        }
        return null
    }

    override fun describe(s: Session): String {
        val sb = StringBuilder()
        sb.append(table.describe(s))
        for (p in joinParts) {
            sb.append(" ${p.joinType} JOIN ${p.table.describe(s)} ON ${s.fullIdentity(p.pkColumn)} = ${s.fullIdentity(p.fkColumn)}" )
        }
        return sb.toString()
    }

    override val columns: List<Column<*>> get() {
        val answer = ArrayList<Column<*>>()
        answer.addAll(table.columns)
        for (p in joinParts)
            answer.addAll(p.table.columns)
        return answer
    }

    // ctor body
    {
        join(otherTable, joinType)
    }
}

open class Table(name: String = ""): ColumnSet() {
    val tableName = if (name.length() > 0) name else this.javaClass.getSimpleName().trimTrailing("Table")

    override val columns = ArrayList<Column<*>>()
    override fun describe(s: Session): String = s.identity(this)

    val primaryKeys  = ArrayList<Column<*>>()
    val indices = ArrayList<Pair<Array<Column<*>>, Boolean>>()

    override val fields: List<Field<*>>
        get() = columns

    fun <T> Column<T>.primaryKey(): PKColumn<T> {
        columns.remove(this)
        val answer = PKColumn<T>(table, name, columnType)
        primaryKeys.add(answer)
        columns.add(answer)
        return answer
    }

    fun <T:Enum<T>> enumeration(name: String, klass: Class<T>) : Column<T> {
        val answer = Column<T>(this, name, EnumerationColumnType(klass))
        columns.add(answer)
        return answer
    }

    fun integer(name: String): Column<Int> {
        val answer = Column<Int>(this, name, IntegerColumnType())
        columns.add(answer)
        return answer
    }

    fun decimal(name: String, scale: Int, precision: Int): Column<BigDecimal> {
        val answer = Column<BigDecimal>(this, name, DecimalColumnType(scale, precision))
        columns.add(answer)
        return answer
    }

    fun long(name: String): Column<Long> {
        val answer = Column<Long>(this, name, LongColumnType())
        columns.add(answer)
        return answer
    }

    fun date(name: String): Column<DateTime> {
        val answer = Column<DateTime>(this, name, DateColumnType(false))
        columns.add(answer)
        return answer
    }

    fun datetime(name: String): Column<DateTime> {
        val answer = Column<DateTime>(this, name, DateColumnType(true))
        columns.add(answer)
        return answer
    }

    fun blob(name: String): Column<Blob> {
        val answer = Column<Blob>(this, name, BlobColumnType())
        columns.add(answer)
        return answer
    }

    fun varchar(name: String, length: Int, collate: String? = null): Column<String> {
        val answer = Column<String>(this, name, StringColumnType(length, collate))
        columns.add(answer)
        return answer
    }

    fun <C:Column<Int>> C.autoIncrement(): C {
        (columnType as IntegerColumnType).autoinc = true
        return this
    }

    fun <T, C:Column<T>> C.references(ref: PKColumn<T>): C {
        referee = ref
        return this
    }

    fun reference(name: String, foreign: IdTable): Column<Int> {
        return integer(name) references foreign.id
    }

    fun optReference(name: String, foreign: IdTable): Column<Int?> {
        return reference(name, foreign).nullable()
    }

    fun <T:Any> Column<T>.nullable(): Column<T?> {
        columnType.nullable = true
        return this as Column<T?>
    }

    fun index (isUnique: Boolean = false, vararg columns: Column<*>) {
        indices.add(columns to isUnique)
    }

    fun<T> Column<T>.index(isUnique: Boolean = false) : Column<T> {
        this.table.index(isUnique, this)
        return this
    }

    fun<T> Column<T>.uniqueIndex() : Column<T> {
        return this.index(true)
    }

    val ddl: String
        get() = ddl()

    private fun ddl(): String {
        var ddl = StringBuilder("CREATE TABLE IF NOT EXISTS ${Session.get().identity(this)}")
        if (columns.size > 0) {
            ddl.append(" (")
            var c = 0;
            for (column in columns) {
                ddl.append(Session.get().identity(column)).append(" ")
                val colType = column.columnType
                when (colType) {
                    is EnumerationColumnType<*>,
                    is IntegerColumnType -> ddl.append("INT")
                    is DecimalColumnType -> ddl.append("DECIMAL(${colType.scale}, ${colType.precision})")
                    is LongColumnType -> ddl.append("BIGINT")
                    is StringColumnType -> {
                        ddl.append("VARCHAR(${colType.length})")
                        if (colType.collate != null)
                            ddl.append(" COLLATE ${colType.collate}")
                    }
                    is DateColumnType -> if (colType.time) ddl.append("DATETIME") else ddl.append("DATE")
                    is BlobColumnType -> ddl.append("BLOB")
                    else -> throw IllegalStateException()
                }
                ddl.append(" ")
                if (column is PKColumn<*>) {
                    ddl.append("PRIMARY KEY ")
                }
                if (colType is IntegerColumnType && colType.autoinc) {
                    ddl.append(Session.get().autoIncrement(column)).append(" ")
                }
                if (colType.nullable) {
                    ddl.append("NULL")
                } else {
                    ddl.append("NOT NULL")
                }
                c++
                if (c < columns.size) {
                    ddl.append(", ")
                }
            }

            ddl.append(")")
        }
        return ddl.toString()
    }
}
