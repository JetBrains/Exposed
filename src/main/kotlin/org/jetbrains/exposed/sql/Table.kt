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

typealias JoinCondition = Pair<Expression<*>, Expression<*>>

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
    abstract fun crossJoin(otherTable: ColumnSet) : Join

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
    FULL,
    CROSS
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

    internal class JoinPart(val joinType: JoinType, val joinPart: ColumnSet, val conditions: List<JoinCondition>, val additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null) {
        init {
            if (joinType != JoinType.CROSS && conditions.isEmpty() && additionalConstraint == null)
                error("Missing join condition on $${this.joinPart}")
        }
    }

    internal val joinParts: ArrayList<JoinPart> = ArrayList()

    override infix fun innerJoin(otherTable: ColumnSet): Join = join(otherTable, JoinType.INNER)

    override infix fun leftJoin(otherTable: ColumnSet): Join = join(otherTable, JoinType.LEFT)

    override infix fun crossJoin(otherTable: ColumnSet): Join = join(otherTable, JoinType.CROSS)

    private fun join(otherTable: ColumnSet, joinType: JoinType = JoinType.INNER, additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null): Join {
        val fkKeys = findKeys (this, otherTable) ?: findKeys (otherTable, this) ?: emptyList()
        when {
            joinType != JoinType.CROSS && fkKeys.isEmpty() && additionalConstraint == null ->
                error("Cannot join with $otherTable as there is no matching primary key/ foreign key pair and constraint missing")

            fkKeys.any { it.second.count() > 1 } && additionalConstraint == null ->  {
                val references = fkKeys.joinToString(" & ") { "${it.first} -> ${it.second.joinToString { it.toString() }}" }
                error("Cannot join with $otherTable as there is multiple primary key <-> foreign key references.\n$references")
            }
            else -> return join(otherTable, joinType, fkKeys.map { it.first to it.second.single() }, additionalConstraint)
        }
    }

    override fun join(otherTable: ColumnSet, joinType: JoinType, onColumn: Expression<*>?, otherColumn: Expression<*>?, additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)?): Join {
        val cond = if (onColumn != null && otherColumn != null) { listOf(JoinCondition(onColumn, otherColumn)) } else emptyList()
        return join(otherTable, joinType, cond, additionalConstraint)
    }

    private fun join(otherTable: ColumnSet, joinType: JoinType, cond: List<JoinCondition>, additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)?) : Join =
        Join(table).also {
            it.joinParts.addAll(this.joinParts)
            it.joinParts.add(JoinPart(joinType, otherTable, cond, additionalConstraint))
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
            append(" ${p.joinType} JOIN ${p.joinPart.describe(s)}")
            if (p.joinType != JoinType.CROSS) {
                append(" ON ")
                val queryBuilder = QueryBuilder(false)
                append(p.conditions.joinToString (" AND "){ (pkColumn, fkColumn) ->
                    "${pkColumn.toSQL(queryBuilder)} = ${fkColumn.toSQL(queryBuilder)}"
                })

                if (p.additionalConstraint != null) {
                    if (p.conditions.isNotEmpty()) append(" AND ")
                    append(" (${SqlExpressionBuilder.(p.additionalConstraint)().toSQL(queryBuilder)})")
                }
            }
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

    override fun join(otherTable: ColumnSet, joinType: JoinType, onColumn: Expression<*>?, otherColumn: Expression<*>?, additionalConstraint: (SqlExpressionBuilder.()->Op<Boolean>)? ) : Join
            = Join (this, otherTable, joinType, onColumn, otherColumn, additionalConstraint)

    @Deprecated("Just an alias to innerJoin", replaceWith = ReplaceWith("this innerJoin otherTable"))
    infix fun join(otherTable: ColumnSet) : Join = Join (this, otherTable, JoinType.INNER)

    override infix fun innerJoin(otherTable: ColumnSet) : Join = Join (this, otherTable, JoinType.INNER)

    override infix fun leftJoin(otherTable: ColumnSet) : Join = Join (this, otherTable, JoinType.LEFT)

    override infix fun crossJoin(otherTable: ColumnSet) : Join = Join (this, otherTable, JoinType.CROSS)

    fun <T> registerColumn(name: String, type: IColumnType): Column<T> = Column<T>(this, name, type).apply {
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
    fun <T:Comparable<T>> Column<T>.entityId(): Column<EntityID<T>> = replaceColumn(this, Column<EntityID<T>>(table, name, EntityIDColumnType(this)).apply {
        this.indexInPK = this@entityId.indexInPK
        this.defaultValueFun = this@entityId.defaultValueFun?.let { { EntityID(it(), table as IdTable<T>) } }
    })

    fun <ID:Comparable<ID>> entityId(name: String, table: IdTable<ID>) : Column<EntityID<ID>> {
        val originalColumn = (table.id.columnType as EntityIDColumnType<*>).idColumn
        val columnTypeCopy = originalColumn.columnType.let { (it as? AutoIncColumnType)?.delegate ?: it }.clone()
        val answer = Column<EntityID<ID>>(this, name, EntityIDColumnType(Column<ID>(table, name, columnTypeCopy)))
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

    /**
     * An enumeration column where enumerations are stored by their ordinal integer.
     *
     * @param name The column name
     * @param klass The enum class
     */
    fun <T:Enum<T>> enumeration(name: String, klass: Class<T>): Column<T> = registerColumn(name, EnumerationColumnType(klass))

    /**
     * An enumeration column where enumerations are stored by their name.
     *
     * @param name The column name
     * @param length The maximum length of the enumeration name
     * @param klass The enum class
     */
    fun <T:Enum<T>> enumerationByName(name: String, length: Int, klass: Class<T>): Column<T> = registerColumn(name, EnumerationNameColumnType(klass, length))

    /**
     * An integer column to store an integer number.
     *
     * @param name The column name
     */
    fun integer(name: String): Column<Int> = registerColumn(name, IntegerColumnType())

    /**
     * A char column to store a single character.
     *
     * @param name The column name
     */
    fun char(name: String): Column<Char> = registerColumn(name, CharacterColumnType())

    /**
     * A decimal column to store a decimal number with a set [precision] and [scale].
     *
     * [precision] sets the total amount of digits to store (including the digits behind the decimal point).
     * [scale] sets the amount of digits to store behind the decimal point.
     *
     * So to store the decimal 123.45, [precision] would have to be set to 5 (as there are five digits in total) and [scale] to 2 (as there are two digits behind the decimal point).
     *
     * @param name The column name
     * @param precision The amount of digits to store in total (including the digits behind the decimal point)
     * @param scale The amount of digits to store behind the decimal point
     */
    fun decimal(name: String, precision: Int, scale: Int): Column<BigDecimal> = registerColumn(name, DecimalColumnType(precision, scale))

    /**
     * A long column to store a large (long) number.
     *
     * @param name The column name
     */
    fun long(name: String): Column<Long> = registerColumn(name, LongColumnType())

    /**
     * A date column to store a date.
     *
     * @param name The column name
     */
    fun date(name: String): Column<DateTime> = registerColumn(name, DateColumnType(false))

    /**
     * A bool column to store a boolean value.
     *
     * @param name The column name
     */
    fun bool(name: String): Column<Boolean> = registerColumn(name, BooleanColumnType())

    /**
     * A datetime column to store both a date and a time.
     *
     * @param name The column name
     */
    fun datetime(name: String): Column<DateTime> = registerColumn(name, DateColumnType(true))

    /**
     * A blob column to store a large amount of binary data.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.EntityTests.testBlobField
     *
     * @param name The column name
     */
    fun blob(name: String): Column<Blob> = registerColumn(name, BlobColumnType())

    /**
     * A text column to store a large amount of text.
     *
     * @param name The column name
     * @param collate The text collate type. Set to null to use the default type.
     */
    fun text(name: String, collate: String? = null): Column<String> = registerColumn(name, TextColumnType(collate))

    /**
     * A binary column to store an array of bytes.
     *
     * @param name The column name
     * @param length The maximum amount of bytes to store
     */
    fun binary(name: String, length: Int): Column<ByteArray> = registerColumn(name, BinaryColumnType(length))

    /**
     * A uuid column to store a UUID.
     *
     * @param name The column name
     */
    fun uuid(name: String) = registerColumn<UUID>(name, UUIDColumnType())

    /**
     * A varchar column to store a string with a set maximum amount of characters.
     *
     * @param name The column name
     * @param length The maximum amount of characters
     * @param collate The text collate type. Set to null to use the default type.
     */
    fun varchar(name: String, length: Int, collate: String? = null): Column<String> = registerColumn(name, VarCharColumnType(length, collate))

    private fun <T> Column<T>.cloneWithAutoInc(idSeqName: String?) : Column<T> = when(columnType) {
        is AutoIncColumnType -> this
        is ColumnType ->
            this@cloneWithAutoInc.clone<Column<T>>(mapOf(Column<T>::columnType to AutoIncColumnType(columnType, idSeqName ?: "${tableName}_${name}_seq")))
        else -> error("Unsupported column type for auto-increment $columnType")
    }

    fun <N:Any> Column<N>.autoIncrement(idSeqName: String? = null): Column<N> = cloneWithAutoInc(idSeqName).apply {
        replaceColumn(this@autoIncrement, this)
    }


    fun <N:Comparable<N>> Column<EntityID<N>>.autoinc(idSeqName: String? = null): Column<EntityID<N>> = cloneWithAutoInc(idSeqName).apply {
        replaceColumn(this@autoinc, this)
    }

    fun <T, S: T, C:Column<S>> C.references(ref: Column<T>, onDelete: ReferenceOption?): C = apply {
        referee = ref
        this.onDelete = onDelete
    }

    infix fun <T, S: T, C:Column<S>> C.references(ref: Column<T>): C = references(ref, null)

    fun <T:Comparable<T>> reference(name: String, foreign: IdTable<T>, onDelete: ReferenceOption? = null): Column<EntityID<T>> =
            entityId(name, foreign).references(foreign.id, onDelete)

    fun<T> Table.reference(name: String, pkColumn: Column<T>): Column<T> {
        val column = Column<T>(this, name, pkColumn.columnType) references pkColumn
        this._columns.add(column)
        return column
    }

    fun <T:Comparable<T>> optReference(name: String, foreign: IdTable<T>, onDelete: ReferenceOption? = null): Column<EntityID<T>?> =
            entityId(name, foreign).references(foreign.id, onDelete).nullable()

    fun <T:Any> Column<T>.nullable(): Column<T?> {
        val newColumn = Column<T?> (table, name, columnType)
        newColumn.referee = referee
        newColumn.onDelete = onDelete.takeIf { it != currentDialectIfAvailable?.defaultReferenceOption }
        newColumn.defaultValueFun = defaultValueFun
        @Suppress("UNCHECKED_CAST")
        newColumn.dbDefaultValue = dbDefaultValue as Expression<T?>?
        newColumn.columnType.nullable = true
        return replaceColumn (this, newColumn)
    }

    fun <T:Any> Column<T>.default(defaultValue: T): Column<T> {
        this.dbDefaultValue = SqlExpressionBuilder.run {
            asLiteral(defaultValue)
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
            append(TransactionManager.current().identity(this@Table))
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

    override fun dropStatement() : List<String> {
        val dropTableDDL = buildString {
            append("DROP TABLE ")
            if (currentDialect.supportsIfNotExists) {
                append(" IF EXISTS ")
            }
            append(TransactionManager.current().identity(this@Table))
            if (currentDialectIfAvailable is OracleDialect) {
                append(" CASCADE CONSTRAINTS")
            }
        }
        val seqDDL = autoIncColumn?.autoIncSeqName?.let {
            Seq(it).dropStatement()
        }.orEmpty()

        return listOf(dropTableDDL) + seqDDL
    }

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