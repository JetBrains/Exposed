package kotlin.sql

import java.util.ArrayList
import java.sql.Date

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

    fun join (another: ColumnSet) : ColumnSet {
        return joinImpl (another, JoinType.LEFT)
    }
    fun innerJoin (another: ColumnSet) : ColumnSet {
        return joinImpl (another, JoinType.INNER)
    }

    private fun joinImpl(another: ColumnSet, joinType : JoinType): ColumnSet {
        return tryJoin(this, another, joinType) ?: tryJoin(another, this, joinType) ?: throw RuntimeException("Can't find pair of column to join with")
    }
}

private fun tryJoin(a: ColumnSet, b: ColumnSet, joinType : JoinType): Join? {
    val a_pk = a.columns.find { it is PKColumn<*> }
    if (a_pk == null) return null

    val b_fk = b.columns.find { it.referee == a_pk }
    if (b_fk == null) return null

    return Join(a, b, a_pk, b_fk, joinType)
}

class Slice(override val source: ColumnSet, override val fields: List<Field<*>>): FieldSet

enum class JoinType {
    INNER
    LEFT
    RIGHT
    FULL
}
class Join(val a: ColumnSet, val b: ColumnSet, val a_pk: Column<*>, val b_fk: Column<*>, val joinType: JoinType): ColumnSet() {
    override fun describe(s: Session): String {
        return "${a.describe(s)} $joinType JOIN ${b.describe(s)} ON ${s.fullIdentity(a_pk)} = ${s.fullIdentity(b_fk)}"
    }

    override val columns: List<Column<*>> get() {
        val answer = ArrayList<Column<*>>()
        answer.addAll(a.columns)
        answer.addAll(b.columns)
        return answer
    }
}

open class Table(name: String = ""): ColumnSet() {
    val tableName = if (name.length() > 0) name else this.javaClass.getSimpleName()

    override val columns = ArrayList<Column<*>>()
    override fun describe(s: Session): String = s.identity(this)

    val primaryKeys  = ArrayList<Column<*>>()
    val foreignKeys  = ArrayList<ForeignKey>()

    override val fields: List<Field<*>>
        get() = columns

    fun <T> Column<T>.primaryKey(): PKColumn<T> {
        columns.remove(this)
        val answer = PKColumn<T>(table, name, columnType)
        primaryKeys.add(answer)
        columns.add(answer)
        return answer
    }

    fun integer(name: String): Column<Int> {
        val answer = Column<Int>(this, name, IntegerColumnType())
        columns.add(answer)
        return answer
    }

    fun date(name: String): Column<Date> {
        val answer = Column<Date>(this, name, DateColumnType())
        columns.add(answer)
        return answer
    }

    fun varchar(name: String, length: Int): Column<String> {
        val answer = Column<String>(this, name, StringColumnType(length))
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

    fun <T:Any> Column<T>.nullable(): Column<T?> {
        columnType.nullable = true
        return this
    }

    internal fun foreignKey(column: Column<*>, table: Table): ForeignKey {
        val foreignKey = ForeignKey(this, column, table)
        foreignKeys.add(foreignKey)
        return foreignKey
    }

    val ddl: String
        get() = ddl()

    private fun ddl(): String {
        var ddl = StringBuilder("CREATE TABLE ${Session.get().identity(this)}")
        if (columns.size > 0) {
            ddl.append(" (")
            var c = 0;
            for (column in columns) {
                ddl.append(Session.get().identity(column)).append(" ")
                val colType = column.columnType
                when (colType) {
                    is IntegerColumnType -> ddl.append("INT")
                    is StringColumnType -> ddl.append("VARCHAR(${colType.length})")
                    is DateColumnType -> ddl.append("DATE")
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
