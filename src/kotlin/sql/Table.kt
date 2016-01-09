package kotlin.sql

import org.joda.time.DateTime
import java.math.BigDecimal
import java.sql.Blob
import java.util.*
import kotlin.dao.EntityID
import kotlin.dao.IdTable

interface FieldSet {
    val fields: List<Expression<*>>
    val source: ColumnSet
}

abstract class ColumnSet(): FieldSet {
    abstract val columns: List<Column<*>>
    override val fields: List<Expression<*>> get() = columns
    override val source = this

    abstract fun describe(s: Transaction): String

    fun slice(vararg columns: Expression<*>): FieldSet = Slice(this, listOf(*columns))
    fun slice(columns: List<Expression<*>>): FieldSet = Slice(this, columns)
}

class Slice(override val source: ColumnSet, override val fields: List<Expression<*>>): FieldSet

enum class JoinType {
    INNER,
    LEFT,
    RIGHT,
    FULL
}

infix fun Table.join (otherTable: Table) : Join {
    return Join (this, otherTable, JoinType.INNER)
}

fun Table.join (otherTable: Table, joinType: JoinType, onColumn: Column<*>? = null, otherColumn: Column<*>? = null, additionalConstraint: (SqlExpressionBuilder.()->Op<Boolean>)? = null) : Join {
    return Join (this, otherTable, joinType, onColumn, otherColumn, additionalConstraint)
}

infix fun Table.innerJoin (otherTable: Table) : Join {
    return Join (this, otherTable, JoinType.INNER)
}

infix fun Table.leftJoin (otherTable: Table) : Join {
    return Join (this, otherTable, JoinType.LEFT)
}

class Join (val table: Table) : ColumnSet() {

    public constructor(table: Table, otherTable: Table, joinType: JoinType = JoinType.INNER, onColumn: Column<*>? = null, otherColumn: Column<*>? = null, additionalConstraint: (SqlExpressionBuilder.()->Op<Boolean>)? = null) : this(table) {
        val new = if (onColumn != null && otherColumn != null) {
            join(otherTable, joinType, onColumn, otherColumn, additionalConstraint)
        } else {
            join(otherTable, joinType, additionalConstraint)
        }
        joinParts.addAll(new.joinParts)
    }

    class JoinPart (val joinType: JoinType, val table: Table, val pkColumn: Expression<*>, val fkColumn: Expression<*>, val additionalConstraint: (SqlExpressionBuilder.()->Op<Boolean>)? = null) {
    }

    val joinParts: ArrayList<JoinPart> = ArrayList();

    infix fun innerJoin (otherTable: Table) : Join {
        return join(otherTable, JoinType.INNER)
    }

    infix fun leftJoin (otherTable: Table) : Join {
        return join(otherTable, JoinType.LEFT)
    }

    fun join (otherTable: Table, joinType: JoinType = JoinType.INNER, additionalConstraint: (SqlExpressionBuilder.()->Op<Boolean>)? = null) : Join {
        val keysPair = findKeys (this, otherTable) ?: findKeys (otherTable, this)
        if (keysPair == null) error ("Cannot join with ${otherTable.tableName} as there is no matching primary key/ foreign key pair")

        return join(otherTable, joinType, keysPair.first, keysPair.second, additionalConstraint)
    }

    fun join(otherTable: Table, joinType: JoinType, onColumn: Expression<*>, otherColumn: Expression<*>, additionalConstraint: (SqlExpressionBuilder.()->Op<Boolean>)? = null): Join {
        val newJoin = Join(table)
        newJoin.joinParts.addAll(joinParts)
        newJoin.joinParts.add(JoinPart(joinType, otherTable, onColumn, otherColumn, additionalConstraint))
        return newJoin
    }

    private fun findKeys(a: ColumnSet, b: ColumnSet): Pair<Column<*>, Column<*>>? {
        for (a_pk in a.columns) {
            val b_fk = b.columns.firstOrNull { it.referee == a_pk }
            if (b_fk != null)
                return a_pk to b_fk
        }
        return null
    }

    override fun describe(s: Transaction): String {
        val sb = StringBuilder()
        sb.append(table.describe(s))
        for (p in joinParts) {
            sb.append(" ${p.joinType} JOIN ${p.table.describe(s)} ON ${p.pkColumn.toSQL(QueryBuilder(false))} = ${p.fkColumn.toSQL(QueryBuilder(false))}" )
            if (p.additionalConstraint != null)
                sb.append(" and (${SqlExpressionBuilder.(p.additionalConstraint)().toSQL(QueryBuilder(false))})")
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
}

open class Table(name: String = ""): ColumnSet(), DdlAware {
    open val tableName = if (name.length > 0) name else this.javaClass.simpleName.removeSuffix("Table")

    private val _columns = ArrayList<Column<*>>()
    override val columns: List<Column<*>> = _columns
    override fun describe(s: Transaction): String = s.identity(this)

    val primaryKeys  = ArrayList<Column<*>>()
    val indices = ArrayList<Pair<Array<out Column<*>>, Boolean>>()

    override val fields: List<Expression<*>>
        get() = columns

    private fun<TColumn: Column<*>> replaceColumn (oldColumn: Column<*>, newColumn: TColumn) : TColumn {
        _columns.remove(oldColumn)
        _columns.add(newColumn)
        return newColumn
    }

    fun <T> Column<T>.primaryKey(): PKColumn<T> {
        val answer = replaceColumn (this, PKColumn<T>(table, name, columnType))
        primaryKeys.add(answer)
        return answer
    }

    fun <T:Enum<T>> enumeration(name: String, klass: Class<T>) : Column<T> {
        val answer = Column<T>(this, name, EnumerationColumnType(klass))
        _columns.add(answer)
        return answer
    }

    fun entityId(name: String, table: IdTable) : Column<EntityID> {
        val answer = Column<EntityID>(this, name, EntityIDColumnType(table))
        _columns.add(answer)
        return answer
    }

    fun integer(name: String): Column<Int> {
        val answer = Column<Int>(this, name, IntegerColumnType())
        _columns.add(answer)
        return answer
    }

    fun char(name: String): Column<Char> {
        val answer = Column<Char>(this, name, CharacterColumnType())
        _columns.add(answer)
        return answer
    }

    fun decimal(name: String, scale: Int, precision: Int): Column<BigDecimal> {
        val answer = Column<BigDecimal>(this, name, DecimalColumnType(scale, precision))
        _columns.add(answer)
        return answer
    }

    fun long(name: String): Column<Long> {
        val answer = Column<Long>(this, name, LongColumnType())
        _columns.add(answer)
        return answer
    }

    fun date(name: String): Column<DateTime> {
        val answer = Column<DateTime>(this, name, DateColumnType(false))
        _columns.add(answer)
        return answer
    }

    fun bool(name: String): Column<Boolean> {
        val answer = Column<Boolean>(this, name, BooleanColumnType())
        _columns.add(answer)
        return answer
    }

    fun datetime(name: String): Column<DateTime> {
        val answer = Column<DateTime>(this, name, DateColumnType(true))
        _columns.add(answer)
        return answer
    }

    fun blob(name: String): Column<Blob> {
        val answer = Column<Blob>(this, name, BlobColumnType())
        _columns.add(answer)
        return answer
    }

    fun text(name: String): Column<String> {
        val answer = Column<String>(this, name, StringColumnType())
        _columns.add(answer)
        return answer
    }

    fun varchar(name: String, length: Int, collate: String? = null): Column<String> {
        val answer = Column<String>(this, name, StringColumnType(length, collate))
        _columns.add(answer)
        return answer
    }

    fun <C:Column<Int>> C.autoIncrement(): C {
        (columnType as IntegerColumnType).autoinc = true
        return this
    }

    fun <C:Column<EntityID>> C.autoinc(): C {
        (columnType as EntityIDColumnType).autoinc = true
        return this
    }

    infix fun <T, S: T, C:Column<S>> C.references(ref: Column<T>): C {
        referee = ref
        return this
    }

    fun reference(name: String, foreign: IdTable, onDelete: ReferenceOption? = null): Column<EntityID> {
        val column = entityId(name, foreign) references foreign.id
        column.onDelete = onDelete
        return column
    }

    fun<T> Table.reference(name: String, pkColumn: Column<T>): Column<T> {
        val column = Column<T>(this, name, pkColumn.columnType) references pkColumn
        this._columns.add(column)
        return column
    }

    fun optReference(name: String, foreign: IdTable, onDelete: ReferenceOption? = null): Column<EntityID?> {
        val column = reference(name, foreign).nullable()
        column.onDelete = onDelete
        return column
    }

    fun <T:Any> Column<T>.nullable(): Column<T?> {
        val newColumn = Column<T?> (table, name, columnType)
        newColumn.referee = referee
        newColumn.defaultValue = defaultValue
        newColumn.columnType.nullable = true
        return replaceColumn (this, newColumn)
    }

    fun <T:Any> Column<T>.default(defaultValue: T): Column<T> {
        this.defaultValue = defaultValue
        return this
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
        get() = createStatement()

    override fun createStatement(): String {
        var ddl = StringBuilder("CREATE TABLE IF NOT EXISTS ${Transaction.current().identity(this)}")
        if (columns.isNotEmpty()) {
            ddl.append(" (")
            var c = 0;
            for (column in columns) {
                ddl.append(column.descriptionDdl())
                c++
                if (c < columns.size) {
                    ddl.append(", ")
                }
            }

            ddl.append(")")
        }
        return ddl.toString()
    }

    override fun dropStatement(): String = "DROP TABLE ${Transaction.current().identity(this)}"

    override fun modifyStatement(): String {
        throw UnsupportedOperationException("Use modify on columns and indices")
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Table) return false
        return other.tableName == tableName
    }

    override fun hashCode(): Int = tableName.hashCode()
}

class Alias<T:Table>(val delegate: T, val alias: String) : Table() {

    override val tableName: String get() = alias

    val tableNameWithAlias: String = "${delegate.tableName} AS $alias"

    private fun <T:Any?> Column<T>.clone() = Column<T>(this@Alias, name, columnType)

    override val columns: List<Column<*>> = delegate.columns.map { it.clone() }

    override fun describe(s: Transaction): String = s.identity(this)

    override val fields: List<Expression<*>> = columns

    override fun createStatement(): String = throw UnsupportedOperationException("Unsupported for aliases")

    override fun dropStatement(): String = throw UnsupportedOperationException("Unsupported for aliases")

    override fun modifyStatement(): String = throw UnsupportedOperationException("Unsupported for aliases")

    override val source: ColumnSet = delegate.source

    override fun equals(other: Any?): Boolean {
        if (other !is Alias<*>) return false
        return this.tableNameWithAlias == other.tableNameWithAlias
    }

    override fun hashCode(): Int = tableNameWithAlias.hashCode()

    @Suppress("UNCHECKED_CAST")
    operator fun <T: Any?> get(original: Column<T>): Column<T> = delegate.columns.find { it == original }?.let { it.clone() as? Column<T> } ?: error("Column not found in original table")
}

fun <T:Table> T.alias(alias: String) = Alias(this, alias)
