package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IdTable
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.OracleDialect
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.jetbrains.exposed.sql.vendors.currentDialectIfAvailable
import org.jetbrains.exposed.sql.vendors.inProperCase
import org.joda.time.DateTime
import java.math.BigDecimal
import java.sql.Blob
import java.util.*
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

interface FieldSet {
    val fields: List<Expression<*>>
    val source: ColumnSet
}

abstract class ColumnSet : FieldSet {
    abstract val columns: List<Column<*>>
    override val fields: List<Expression<*>> get() = columns
    override val source
        get() = this

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

    val joinParts: ArrayList<JoinPart> = ArrayList()

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
                val references = fkKeys.map { "${it.first} -> ${it.second.joinToString { it.toString() }}" }.joinToString(" & ")
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

    val autoIncColumn: Column<*>? get() = columns.firstOrNull { it.columnType.isAutoInc }

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
        val columnTypeCopy = originalColumn.columnType.let { (it as? AutoIncColumnType)?.delegate ?: it }.clone()
        val answer = Column<EntityID<ID>>(this, name, EntityIDColumnType(Column(table, name, columnTypeCopy)))
        _columns.add(answer)
        return answer
    }

    private fun <T:Any> T.clone(replaceArgs: Map<KProperty1<T,*>, Any> = emptyMap()) = javaClass.kotlin.run {
        val consParams = primaryConstructor!!.parameters
        val allParams = memberProperties
                .filter { it is KMutableProperty1<T, *> || it.name in consParams.map { it.name } }
                .associate { it.name to (replaceArgs[it] ?: it.get(this@clone)) }
        primaryConstructor!!.callBy(consParams.associate { it to allParams[it.name] })
    }

    fun <T:Enum<T>> enumeration(name: String, klass: Class<T>): Column<T> = registerColumn(name, EnumerationColumnType(klass))

    fun <T:Enum<T>> enumerationByName(name: String, length: Int, klass: Class<T>): Column<T> = registerColumn(name, EnumerationNameColumnType(klass, length))

    fun integer(name: String): Column<Int> = registerColumn(name, IntegerColumnType())

    fun char(name: String): Column<Char> = registerColumn(name, CharacterColumnType())

    fun decimal(name: String, precision: Int, scale: Int): Column<BigDecimal> = registerColumn(name, DecimalColumnType(precision, scale))

    fun long(name: String): Column<Long> = registerColumn(name, LongColumnType())

    fun date(name: String): Column<DateTime> = registerColumn(name, DateColumnType(false))

    fun bool(name: String): Column<Boolean> = registerColumn(name, BooleanColumnType())

    fun datetime(name: String): Column<DateTime> = registerColumn(name, DateColumnType(true))

    fun blob(name: String): Column<Blob> = registerColumn(name, BlobColumnType())

    fun text(name: String): Column<String> = registerColumn(name, StringColumnType(length = 65535))

    fun binary(name: String, length: Int): Column<ByteArray> = registerColumn(name, BinaryColumnType(length))

    fun uuid(name: String) = registerColumn<UUID>(name, UUIDColumnType())

    fun varchar(name: String, length: Int, collate: String? = null): Column<String> = registerColumn(name, StringColumnType(length, collate))

    private fun <T> Column<T>.cloneWithAutoInc(idSeqName: String?) : Column<T> = when(columnType) {
        is AutoIncColumnType -> this
        is ColumnType ->
            this@cloneWithAutoInc.clone<Column<T>>(mapOf(Column<T>::columnType to AutoIncColumnType(columnType, idSeqName ?: "${tableName}_${name}_seq")))
        else -> error("Unsupported column type for auto-increment $columnType")
    }

    fun <N:Number> Column<N>.autoIncrement(idSeqName: String? = null): Column<N> = cloneWithAutoInc(idSeqName).apply {
        replaceColumn(this@autoIncrement, this)
    }


    fun <N:Number> Column<EntityID<N>>.autoinc(idSeqName: String? = null): Column<EntityID<N>> = cloneWithAutoInc(idSeqName).apply {
        replaceColumn(this@autoinc, this)
    }


    fun <T, S: T, C:Column<S>> C.references(ref: Column<T>, onDelete: ReferenceOption?): C {
        return this.apply {
            referee = ref
            this.onDelete = onDelete
        }
    }

    infix fun <T, S: T, C:Column<S>> C.references(ref: Column<T>): C = references(ref, null)

    fun <T:Any> reference(name: String, foreign: IdTable<T>, onDelete: ReferenceOption? = null): Column<EntityID<T>> {
        return entityId(name, foreign).references(foreign.id, onDelete)
    }

    fun<T> Table.reference(name: String, pkColumn: Column<T>): Column<T> {
        val column = Column<T>(this, name, pkColumn.columnType) references pkColumn
        this._columns.add(column)
        return column
    }

    fun <T:Any> optReference(name: String, foreign: IdTable<T>, onDelete: ReferenceOption? = null): Column<EntityID<T>?> {
        return entityId(name, foreign).references(foreign.id, onDelete).nullable()
    }

    fun <T:Any> Column<T>.nullable(): Column<T?> {
        val newColumn = Column<T?> (table, name, columnType)
        newColumn.referee = referee
        newColumn.onDelete = onDelete
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

    fun uniqueIndex(vararg columns: Column<*>) {
        index(true, *columns)
    }

    val ddl: List<String>
        get() = createStatement()

    override fun createStatement(): List<String> {
        val seqDDL = autoIncColumn?.autoIncSeqName?.let {
            Seq(it).createStatement()
        }.orEmpty()

        val createTableDDL = buildString {
            append("CREATE TABLE ")
            if (currentDialect.supportsIfNotExists) {
                append("IF NOT EXISTS ")
            }
            append(TransactionManager.current().identity(this@Table).inProperCase())
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
        }
        return seqDDL + createTableDDL
    }

    internal fun primaryKeyConstraint(): String? {
        var pkey = columns.filter { it.indexInPK != null }.sortedBy { it.indexInPK }
        if (pkey.isEmpty()) {
            pkey = columns.filter { it.columnType.isAutoInc }
        }
        if (pkey.isNotEmpty()) {
            return pkey.joinToString(
                    prefix = "CONSTRAINT ${TransactionManager.current().quoteIfNecessary("pk_$tableName")} PRIMARY KEY (", postfix = ")") {
                TransactionManager.current().identity(it)
            }
        }
        return null
    }

    override fun dropStatement() = listOf("DROP TABLE ${TransactionManager.current().identity(this)}" +
        if (currentDialectIfAvailable == OracleDialect) { " CASCADE CONSTRAINTS" } else "") +
        autoIncColumn?.autoIncSeqName?.let { Seq(it).dropStatement() }.orEmpty()

    override fun modifyStatement() = throw UnsupportedOperationException("Use modify on columns and indices")

    override fun equals(other: Any?): Boolean {
        if (other !is Table) return false
        return other.tableName == tableName
    }

    override fun hashCode(): Int = tableName.hashCode()
}

data class Seq(private val name: String) {
    fun createStatement() = listOf("CREATE SEQUENCE $name")
    fun dropStatement() = listOf("DROP SEQUENCE $name")
}

fun ColumnSet.targetTables(): List<Table> = when (this) {
    is Alias<*> -> listOf(this.delegate)
    is QueryAlias -> this.query.set.source.targetTables()
    is Table -> listOf(this)
    is Join -> this.table.targetTables() + this.joinParts.flatMap { it.joinPart.targetTables() }
    else -> error("No target provided for update")
}