package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable
import org.joda.time.DateTime
import java.math.BigDecimal
import java.sql.Blob
import java.util.*

interface FieldSet {
    val fields: List<Expression<*>>
    val source: ColumnSet
}

abstract class ColumnSet(): FieldSet {
    abstract val columns: List<Column<*>>
    override val fields: List<Expression<*>> get() = columns
    override val source = this

    abstract fun describe(s: Transaction): String

    abstract fun join(otherTable: ColumnSet, joinType: JoinType, onColumn: Expression<*>? = null, otherColumn: Expression<*>? = null, additionalConstraint: (SqlExpressionBuilder.()->Op<Boolean>)? = null): Join
    abstract fun innerJoin(otherTable: ColumnSet): Join
    abstract fun leftJoin(otherTable: ColumnSet) : Join

    fun slice(vararg columns: Expression<*>): FieldSet = Slice(this, listOf(*columns))
    fun slice(columns: List<Expression<*>>): FieldSet = Slice(this, columns)
}

fun <C1:ColumnSet, C2:ColumnSet> C1.innerJoin(otherTable: C2, onColumn: C1.() -> Expression<*>, otherColumn: C2.() -> Expression<*>) = join(otherTable, JoinType.INNER, onColumn(this), otherColumn(otherTable))

fun <C1:ColumnSet, C2:ColumnSet> C1.leftJoin(otherTable: C2, onColumn: C1.() -> Expression<*>, otherColumn: C2.() -> Expression<*>) = join(otherTable, JoinType.LEFT, onColumn(), otherTable.otherColumn())

class Slice(override val source: ColumnSet, override val fields: List<Expression<*>>): FieldSet

enum class JoinType {
    INNER,
    LEFT,
    RIGHT,
    FULL
}

class Join (val table: ColumnSet) : ColumnSet() {

    constructor(table: ColumnSet, otherTable: ColumnSet, joinType: JoinType = JoinType.INNER, onColumn: Expression<*>? = null, otherColumn: Expression<*>? = null, additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null) : this(table) {
        val new = if (onColumn != null && otherColumn != null) {
            join(otherTable, joinType, onColumn, otherColumn, additionalConstraint)
        } else {
            join(otherTable, joinType, additionalConstraint)
        }
        joinParts.addAll(new.joinParts)
    }

    class JoinPart(val joinType: JoinType, val joinPart: ColumnSet, val pkColumn: Expression<*>? = null, val fkColumn: Expression<*>? = null, val additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null) {
        init {
            if (!(pkColumn != null && fkColumn != null || additionalConstraint != null))
                error("Missing join condition on $${this.joinPart}")
        }
    }

    val joinParts: ArrayList<JoinPart> = ArrayList();

    override infix fun innerJoin(otherTable: ColumnSet): Join {
        return join(otherTable, JoinType.INNER)
    }

    override infix fun leftJoin(otherTable: ColumnSet): Join {
        return join(otherTable, JoinType.LEFT)
    }

    private fun join(otherTable: ColumnSet, joinType: JoinType = JoinType.INNER, additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null): Join {
        val fkKeys = findKeys (this, otherTable) ?: findKeys (otherTable, this) ?: emptyList()
        when {
            fkKeys.isEmpty() && additionalConstraint == null ->
                error ("Cannot join with $otherTable as there is no matching primary key/ foreign key pair and constraint missing")

            fkKeys.count() > 1 || fkKeys.any { it.second.count() > 1 } ->  {
                val references = fkKeys.map { "${it.first.toString()} -> ${it.second.joinToString { it.toString() }}" }.joinToString(" & ")
                error("Cannot join with $otherTable as there is multiple primary key <-> foreign key references.\n$references")
            }
            else -> return join(otherTable, joinType, fkKeys.singleOrNull()?.first, fkKeys.singleOrNull()?.second?.single(), additionalConstraint)
        }
    }

    override fun join(otherTable: ColumnSet, joinType: JoinType, onColumn: Expression<*>?, otherColumn: Expression<*>?, additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)?): Join {
        val newJoin = Join(table)
        newJoin.joinParts.addAll(joinParts)
        newJoin.joinParts.add(JoinPart(joinType, otherTable, onColumn, otherColumn, additionalConstraint))
        return newJoin
    }

    private fun findKeys(a: ColumnSet, b: ColumnSet): List<Pair<Column<*>, List<Column<*>>>>? {

        val pkToFKeys = a.columns.map { a_pk ->
            a_pk to b.columns.filter { it.referee == a_pk }
        }.filter { it.second.isNotEmpty() }

        return if (pkToFKeys.isNotEmpty()) pkToFKeys else null
    }

    override fun describe(s: Transaction): String = buildString {
        append(table.describe(s))
        for (p in joinParts) {
            append(" ${p.joinType} JOIN ${p.joinPart.describe(s)} ON ")
            val queryBuilder = QueryBuilder(false)
            if (p.pkColumn != null && p.fkColumn != null) {
                append("${p.pkColumn.toSQL(queryBuilder)} = ${p.fkColumn.toSQL(queryBuilder)}")
                if (p.additionalConstraint != null) append(" and ")
            }
            if (p.additionalConstraint != null)
                append(" (${SqlExpressionBuilder.(p.additionalConstraint)().toSQL(queryBuilder)})")
        }
    }

    override val columns: List<Column<*>> get() = joinParts.fold(table.columns) { r, j ->
        r + j.joinPart.columns
    }

    fun alreadyInJoin(table: Table) = joinParts.any { it.joinPart == table}
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

    override fun join(otherTable: ColumnSet, joinType: JoinType, onColumn: Expression<*>?, otherColumn: Expression<*>?, additionalConstraint: (SqlExpressionBuilder.()->Op<Boolean>)? ) : Join {
        return Join (this, otherTable, joinType, onColumn, otherColumn, additionalConstraint)
    }

    @Deprecated("Just an alias to innerJoin", replaceWith = ReplaceWith("this innerJoin otherTable"))
    infix fun join(otherTable: ColumnSet) : Join {
        return Join (this, otherTable, JoinType.INNER)
    }

    override infix fun innerJoin(otherTable: ColumnSet) : Join {
        return Join (this, otherTable, JoinType.INNER)
    }

    override infix fun leftJoin(otherTable: ColumnSet) : Join {
        return Join (this, otherTable, JoinType.LEFT)
    }

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

fun ColumnSet.targetTables(): List<Table> = when (this) {
    is Alias<*> -> listOf(this.delegate)
    is QueryAlias -> this.query.set.source.targetTables()
    is Table -> listOf(this)
    is Join -> this.table.targetTables() + this.joinParts.flatMap { it.joinPart.targetTables() }
    else -> error("No target provided for update")
}