package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.inProperCase
import org.joda.time.DateTime
import java.math.BigDecimal
import java.sql.Blob
import java.util.*
import kotlin.reflect.memberProperties
import kotlin.reflect.primaryConstructor

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
    open val tableName = (if (name.isNotEmpty()) name else this.javaClass.simpleName.removeSuffix("Table"))

    fun nameInDatabaseCase() = tableName.inProperCase()

    private val _columns = ArrayList<Column<*>>()
    override val columns: List<Column<*>> = _columns
    override fun describe(s: Transaction): String = s.identity(this)

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

    fun <T> registerColumn(name: String, type: ColumnType): Column<T> = Column<T>(this, name, type).apply {
        _columns.add(this)
    }

    fun<TColumn: Column<*>> replaceColumn (oldColumn: Column<*>, newColumn: TColumn) : TColumn {
        _columns.remove(oldColumn)
        _columns.add(newColumn)
        return newColumn
    }

    fun <T> Column<T>.primaryKey(indx: Int? = null): Column<T> {
        if (indx != null && table.columns.any { it.indexInPK == indx } ) throw IllegalArgumentException("Table $tableName already contains PK at $indx")
        indexInPK = indx ?: table.columns.count { it.indexInPK != null } + 1
        return this
    }

    @Suppress("UNCHECKED_CAST")
    fun <T:Any> Column<T>.entityId(): Column<EntityID<T>> = replaceColumn(this, Column<EntityID<T>>(table, name, EntityIDColumnType(this)).apply {
        this.indexInPK = this@entityId.indexInPK
        this.defaultValueFun = this@entityId.defaultValueFun?.let { { EntityID(it(), table as IdTable<T>) } }
    })

    fun <ID:Any> entityId(name: String, table: IdTable<ID>) : Column<EntityID<ID>> {
        val originalColumn = (table.id.columnType as EntityIDColumnType<*>).idColumn
        val copy = originalColumn.columnType.clone().apply { autoinc = false }
        val answer = Column<EntityID<ID>>(this, name, EntityIDColumnType(Column(table, name, copy), false))
        _columns.add(answer)
        return answer
    }

    private fun <T:Any> T.clone() = javaClass.kotlin.run {
        val allParams = memberProperties.map { it.name to it.get(this@clone) }.toMap()
        primaryConstructor!!.callBy(primaryConstructor!!.parameters.map { it to allParams[it.name] }.toMap())
    }

    fun <T:Enum<T>> enumeration(name: String, klass: Class<T>) : Column<T>  = registerColumn(name, EnumerationColumnType(klass))

    fun integer(name: String): Column<Int> = registerColumn(name, IntegerColumnType())

    fun char(name: String): Column<Char> = registerColumn(name, CharacterColumnType())

    fun decimal(name: String, precision: Int, scale: Int): Column<BigDecimal> = registerColumn(name, DecimalColumnType(precision, scale))

    fun long(name: String): Column<Long> = registerColumn(name, LongColumnType())

    fun date(name: String): Column<DateTime> = registerColumn(name, DateColumnType(false))

    fun bool(name: String): Column<Boolean> = registerColumn(name, BooleanColumnType())

    fun datetime(name: String): Column<DateTime> = registerColumn(name, DateColumnType(true))

    fun blob(name: String): Column<Blob> = registerColumn(name, BlobColumnType())

    fun text(name: String): Column<String> = registerColumn(name, StringColumnType())

    fun binary(name: String, length: Int): Column<ByteArray> = registerColumn(name, BinaryColumnType(length))

    fun uuid(name: String) = registerColumn<UUID>(name, UUIDColumnType())

    fun varchar(name: String, length: Int, collate: String? = null): Column<String> = registerColumn(name, StringColumnType(length, collate))

    fun <N:Number, C:Column<N>> C.autoIncrement(): C {
        columnType.autoinc = true
        return this
    }

    fun <N:Number, C:Column<EntityID<N>>> C.autoinc(): C {
        (columnType as EntityIDColumnType<*>).autoinc = true
        return this
    }

    infix fun <T, S: T, C:Column<S>> C.references(ref: Column<T>): C {
        referee = ref
        return this
    }

    fun <T:Any> reference(name: String, foreign: IdTable<T>, onDelete: ReferenceOption? = null): Column<EntityID<T>> {
        val column = entityId(name, foreign) references foreign.id
        column.onDelete = onDelete
        return column
    }

    fun<T> Table.reference(name: String, pkColumn: Column<T>): Column<T> {
        val column = Column<T>(this, name, pkColumn.columnType) references pkColumn
        this._columns.add(column)
        return column
    }
    fun <T:Any> optReference(name: String, foreign: IdTable<T>, onDelete: ReferenceOption? = null): Column<EntityID<T>?> {
        val column = reference(name, foreign).nullable()
        column.onDelete = onDelete
        return column
    }

    fun <T:Any> Column<T>.nullable(): Column<T?> {
        val newColumn = Column<T?> (table, name, columnType)
        newColumn.referee = referee
        newColumn.defaultValueFun = defaultValueFun
        newColumn.dbDefaultValue = dbDefaultValue
        newColumn.columnType.nullable = true
        return replaceColumn (this, newColumn)
    }

    fun <T:Any> Column<T>.default(defaultValue: T): Column<T> {
        this.dbDefaultValue = object : Expression<T>() {
            override fun toSQL(queryBuilder: QueryBuilder): String {
                return columnType.valueToString(defaultValue)
            }
        }
        this.defaultValueFun = { defaultValue }
        return this
    }

    fun <T:Any> Column<T>.defaultExpression(defaultValue: Expression<T>): Column<T> {
        this.dbDefaultValue = defaultValue
        this.defaultValueFun = null
        return this
    }

    fun <T:Any> Column<T>.clientDefault(defaultValue: () -> T): Column<T> {
        this.defaultValueFun = defaultValue
        this.dbDefaultValue = null
        return this
    }

    fun index (isUnique: Boolean = false, vararg columns: Column<*>) {
        indices.add(columns to isUnique)
    }

    fun<T> Column<T>.index(isUnique: Boolean = false) : Column<T> = apply {
        table.index(isUnique, this)
    }

    fun<T> Column<T>.uniqueIndex() : Column<T> = index(true)

    val ddl: List<String>
        get() = createStatement()

    override fun createStatement() = listOf(buildString {
        append("CREATE TABLE IF NOT EXISTS ${TransactionManager.current().identity(this@Table).inProperCase()}")
        if (columns.any()) {
            append(columns.joinToString(prefix = " (") { it.descriptionDdl() })
            if (columns.none { it.isOneColumnPK() }) {
                primaryKeyConstraint()?.let {
                    append(", $it")
                }
            }
            columns.filter { it.referee != null }.let { references ->
                if (references.isNotEmpty()) {
                    append(references.joinToString(prefix = ", ", separator = ", ") { ForeignKeyConstraint.from(it).foreignKeyPart })
                }
            }

            append(")")
        }
    })

    internal fun primaryKeyConstraint(): String? {
        var pkey = columns.filter { it.indexInPK != null }.sortedBy { it.indexInPK }
        if (pkey.isEmpty()) {
            pkey = columns.filter { it.columnType.autoinc }
        }
        if (pkey.isNotEmpty()) {
            return pkey.joinToString(
                    prefix = "CONSTRAINT ${TransactionManager.current().quoteIfNecessary("pk_$tableName")} PRIMARY KEY (", postfix = ")") {
                TransactionManager.current().identity(it)
            }
        }
        return null
    }

    override fun dropStatement() = listOf("DROP TABLE IF EXISTS ${TransactionManager.current().identity(this)}")

    override fun modifyStatement() = throw UnsupportedOperationException("Use modify on columns and indices")

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