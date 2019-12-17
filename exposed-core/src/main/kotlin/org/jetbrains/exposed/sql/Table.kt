package org.jetbrains.exposed.sql

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.EntityIDFunctionProvider
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.exceptions.DuplicateColumnException
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.*
import java.math.BigDecimal
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubclassOf
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

    /**
     * Appends current [ColumnSet] to a query
     */
    abstract fun describe(s: Transaction, queryBuilder: QueryBuilder)

    /**
     * Create join relation with [otherTable]
     * When all joining options are absent Exposed will try to resolve referencing columns by itself.
     *
     * @param otherTable [ColumnSet] to join with
     * @param joinType See [JoinType] for available options
     * @param onColumn The column from a current [ColumnSet], may be skipped if [additionalConstraint] will be used
     * @param otherColumn The column from an [otherTable], may be skipped if [additionalConstraint] will be used
     * @param additionalConstraint The condition to join which will be placed in ON part of SQL query
     * @throws IllegalStateException If join could not be prepared. See exception message for more details.
     */
    abstract fun join(otherTable: ColumnSet, joinType: JoinType, onColumn: Expression<*>? = null, otherColumn: Expression<*>? = null, additionalConstraint: (SqlExpressionBuilder.()->Op<Boolean>)? = null): Join
    abstract fun innerJoin(otherTable: ColumnSet): Join
    abstract fun leftJoin(otherTable: ColumnSet) : Join
    abstract fun rightJoin(otherTable: ColumnSet) : Join
    abstract fun fullJoin(otherTable: ColumnSet) : Join
    abstract fun crossJoin(otherTable: ColumnSet) : Join

    fun slice(vararg columns: Expression<*>): FieldSet = Slice(this, columns.distinct())
    fun slice(columns: List<Expression<*>>): FieldSet = Slice(this, columns.distinct())
}

fun <C1:ColumnSet, C2:ColumnSet> C1.innerJoin(otherTable: C2, onColumn: C1.() -> Expression<*>, otherColumn: C2.() -> Expression<*>) = join(otherTable, JoinType.INNER, onColumn(this), otherColumn(otherTable))

fun <C1:ColumnSet, C2:ColumnSet> C1.leftJoin(otherTable: C2, onColumn: C1.() -> Expression<*>, otherColumn: C2.() -> Expression<*>) = join(otherTable, JoinType.LEFT, onColumn(), otherTable.otherColumn())

fun <C1:ColumnSet, C2:ColumnSet> C1.rightJoin(otherTable: C2, onColumn: C1.() -> Expression<*>, otherColumn: C2.() -> Expression<*>) = join(otherTable, JoinType.RIGHT, onColumn(), otherTable.otherColumn())

fun <C1:ColumnSet, C2:ColumnSet> C1.fullJoin(otherTable: C2, onColumn: C1.() -> Expression<*>, otherColumn: C2.() -> Expression<*>) = join(otherTable, JoinType.FULL, onColumn(), otherTable.otherColumn())

fun <C1:ColumnSet, C2:ColumnSet> C1.crossJoin(otherTable: C2, onColumn: C1.() -> Expression<*>, otherColumn: C2.() -> Expression<*>) = join(otherTable, JoinType.CROSS, onColumn(), otherTable.otherColumn())

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

    override infix fun rightJoin(otherTable: ColumnSet): Join = join(otherTable, JoinType.RIGHT)

    override infix fun fullJoin(otherTable: ColumnSet): Join = join(otherTable, JoinType.FULL)

    override infix fun crossJoin(otherTable: ColumnSet): Join = join(otherTable, JoinType.CROSS)

    private fun join(otherTable: ColumnSet, joinType: JoinType = JoinType.INNER, additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null): Join {
        val fkKeys = findKeys (this, otherTable) ?: findKeys (otherTable, this) ?: emptyList()
        when {
            joinType != JoinType.CROSS && fkKeys.isEmpty() && additionalConstraint == null ->
                error("Cannot join with $otherTable as there is no matching primary key/ foreign key pair and constraint missing")

            fkKeys.any { it.second.size > 1 } && additionalConstraint == null ->  {
                val references = fkKeys.joinToString(" & ") { "${it.first} -> ${it.second.joinToString()}" }
                error("Cannot join with $otherTable as there is multiple primary key <-> foreign key references.\n$references")
            }
            else -> return join(otherTable, joinType, fkKeys.filter { it.second.size == 1 }.map { it.first to it.second.single() }, additionalConstraint)
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

    override fun describe(s: Transaction, queryBuilder: QueryBuilder)
        = queryBuilder {
            table.describe(s, this)
            for (p in joinParts) {
                append(" ${p.joinType} JOIN ")
                val isJoin = p.joinPart is Join
                if (isJoin) append("(")
                p.joinPart.describe(s, this)
                if(isJoin) append(")")
                if (p.joinType != JoinType.CROSS) {
                    append(" ON ")
                    p.conditions.appendTo (this, " AND "){ (pkColumn, fkColumn) ->
                        append(pkColumn, " = ", fkColumn)
                    }

                    if (p.additionalConstraint != null) {
                        if (p.conditions.isNotEmpty()) append(" AND ")
                        append(" (")
                        append(SqlExpressionBuilder.(p.additionalConstraint)())
                        append(")")
                    }
                }
            }
        }

    override val columns: List<Column<*>> get() = joinParts.fold(table.columns) { r, j ->
        r + j.joinPart.columns
    }

    fun alreadyInJoin(table: Table) = joinParts.any { it.joinPart == table}
}

/**
 * Base class for any simple table.
 *
 * If you want to reference your table use [IdTable] instead.
 *
 * @param name Table name, by default name will be resolved from a class name with "Table" suffix removed (if present)
 */
open class Table(name: String = ""): ColumnSet(), DdlAware {
    open val tableName = if (name.isNotEmpty()) name else this.javaClass.simpleName.removeSuffix("Table")

    internal val tableNameWithoutScheme get() = tableName.substringAfter(".")

    /**
     * @return Table name in proper case. Should be called within transaction or default [tableName] will be returned
     */
    fun nameInDatabaseCase() = tableName.inProperCase()

    private val _columns = ArrayList<Column<*>>()
    override val columns: List<Column<*>> = _columns

    val autoIncColumn: Column<*>? get() = columns.firstOrNull { it.columnType.isAutoInc }

    override fun describe(s: Transaction, queryBuilder: QueryBuilder) = queryBuilder{ append(s.identity(this@Table)) }

    /**
     * Contains all indices declared on that table
     */
    val indices: List<Index> = ArrayList()
    private val checkConstraints = ArrayList<Pair<String, Op<Boolean>>>()

    override val fields: List<Expression<*>>
        get() = columns

    override fun join(otherTable: ColumnSet, joinType: JoinType, onColumn: Expression<*>?, otherColumn: Expression<*>?, additionalConstraint: (SqlExpressionBuilder.()->Op<Boolean>)? ) : Join
            = Join (this, otherTable, joinType, onColumn, otherColumn, additionalConstraint)

    override infix fun innerJoin(otherTable: ColumnSet) : Join = Join (this, otherTable, JoinType.INNER)

    override infix fun leftJoin(otherTable: ColumnSet) : Join = Join (this, otherTable, JoinType.LEFT)

    override infix fun rightJoin(otherTable: ColumnSet) : Join = Join (this, otherTable, JoinType.RIGHT)

    override infix fun fullJoin(otherTable: ColumnSet) : Join = Join (this, otherTable, JoinType.FULL)

    override infix fun crossJoin(otherTable: ColumnSet) : Join = Join (this, otherTable, JoinType.CROSS)

    /**
     * Adds a new column to a table.
     */
    fun <T> registerColumn(name: String, type: IColumnType): Column<T> = Column<T>(this, name, type).apply {
        _columns.addColumn(this)
    }

    /**
     * Replaces [oldColumn] with a [newColumn] in a table.
     * Mostly used internally by the library.
     */
    fun<TColumn: Column<*>> replaceColumn(oldColumn: Column<*>, newColumn: TColumn) : TColumn {
        _columns.remove(oldColumn)
        _columns.addColumn(newColumn)
        return newColumn
    }

    /**
     * Mark @receiver column as primary key.
     *
     * When you define multiple primary keys on a table it will create composite key.
     * Order of columns in a primary key will be the same as order of the columns in a table mapping from top to bottom.
     * If you desire to change the order only in a primary key provide [indx] paramter.
     *
     * @param indx An optional column index in a primary key
     */
    fun <T> Column<T>.primaryKey(indx: Int? = null): Column<T> {
        if (indx != null && table.columns.any { it.indexInPK == indx } ) throw IllegalArgumentException("Table $tableName already contains PK at $indx")
        indexInPK = indx ?: table.columns.count { it.indexInPK != null } + 1
        return this
    }

    @Suppress("UNCHECKED_CAST")
    fun <T:Comparable<T>> Column<T>.entityId(): Column<EntityID<T>> = replaceColumn(this, Column<EntityID<T>>(table, name, EntityIDColumnType(this)).also {
        it.indexInPK = this.indexInPK
        it.defaultValueFun = defaultValueFun?.let { { EntityIDFunctionProvider.createEntityID(it(), table as IdTable<T>) } }
    })

    fun <ID:Comparable<ID>> entityId(name: String, table: IdTable<ID>) : Column<EntityID<ID>> {
        val originalColumn = (table.id.columnType as EntityIDColumnType<*>).idColumn
        val columnTypeCopy = originalColumn.columnType.cloneAsBaseType()
        val answer = Column<EntityID<ID>>(this, name, EntityIDColumnType(Column<ID>(table, name, columnTypeCopy)))
        _columns.addColumn(answer)
        return answer
    }

    private fun IColumnType.cloneAsBaseType() : IColumnType = ((this as? AutoIncColumnType)?.delegate ?: this).clone()

    private fun <T:Any> T.clone(replaceArgs: Map<KProperty1<T,*>, Any> = emptyMap()) = javaClass.kotlin.run {
        val consParams = primaryConstructor!!.parameters
        val mutableProperties = memberProperties.filterIsInstance<KMutableProperty1<T, Any?>>()
        val allValues = memberProperties
                .filter { it in mutableProperties || it.name in consParams.map { it.name } }
                .associate { it.name to (replaceArgs[it] ?: it.get(this@clone)) }
        primaryConstructor!!.callBy(consParams.associateWith { allValues[it.name] }).also { newInstance ->
            mutableProperties.forEach { prop ->
                prop.set(newInstance, allValues[prop.name])
            }
        }
    }

    /**
     * An enumeration column where enumerations are stored by their ordinal integer.
     *
     * @param name The column name
     * @param klass The enum class
     */
    fun <T:Enum<T>> enumeration(name: String, klass: KClass<T>): Column<T> = registerColumn(name, EnumerationColumnType(klass))

    /**
     * An enumeration column where enumerations are stored by their name.
     *
     * @param name The column name
     * @param length The maximum length of the enumeration name
     * @param klass The enum class
     */
    fun <T:Enum<T>> enumerationByName(name: String, length: Int, klass: KClass<T>): Column<T> = registerColumn(name, EnumerationNameColumnType(klass, length))

    /**
     * An enumeration column with custom sql type.
     * The main usage is to use a database specific types.
     * See [https://github.com/JetBrains/Exposed/wiki/DataTypes#how-to-use-database-enum-types] for more details.
     * @param name The column name
     * @param sql A SQL definition for the column
     * @param fromDb A lambda to convert a value received from a database to an enumeration instance
     * @param toDb A lambda to convert an enumeration instance to a value which will be stored to a database
     */
    @Suppress("UNCHECKED_CAST")
    fun <T:Enum<T>> customEnumeration(name: String, sql: String? = null, fromDb: (Any) -> T, toDb: (T) -> Any) =
        registerColumn<T>(name, object : ColumnType() {
            override fun sqlType(): String = sql ?: error("Column $name should exists in database ")
            override fun valueFromDB(value: Any) = if (value::class.isSubclassOf(Enum::class)) value as T else fromDb(value)
            override fun notNullValueToDB(value: Any) = toDb(value as T)
        })

    /**
     * A smallint column to store a short number.
     *
     * @param name The column name
     */
    fun short(name: String): Column<Short> = registerColumn(name, ShortColumnType())

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
     * A float column to store a float number
     *
     * @see decimal for more details
     *
     */
    fun float(name: String): Column<Float> = registerColumn(name, FloatColumnType())

    /**
     * A double column to store a double precision number
     *
     * @see decimal for more details
     *
     */
    fun double(name: String): Column<Double> = registerColumn(name, DoubleColumnType())

    /**
     * A long column to store a large (long) number.
     *
     * @param name The column name
     */
    fun long(name: String): Column<Long> = registerColumn(name, LongColumnType())

    /**
     * A bool column to store a boolean value.
     *
     * @param name The column name
     */
    fun bool(name: String): Column<Boolean> = registerColumn(name, BooleanColumnType())

    /**
     * A blob column to store a large amount of binary data.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.EntityTests.testBlobField
     *
     * @param name The column name
     */
    fun blob(name: String): Column<ExposedBlob> = registerColumn(name, BlobColumnType())

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
     * @sample org.jetbrains.exposed.sql.tests.shared.DDLTests.testBinary
     *
     * @param name The column name
     * @param length The maximum amount of bytes to store, this parameter is necessary only in H2, SQLite, MySQL,
     *               MariaDB, and SQL Server dialects.
     */
    fun binary(name: String, length: Int): Column<ByteArray> = registerColumn(name, BinaryColumnType(length))

    /**
     * A binary column to store an array of bytes. This function is supported only by Oracle and PostgeSQL dialects.
     * If you are using another dialect, please use instead the [binary] function by adding the length parameter.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.DDLTests.testBinaryWithoutLength
     *
     * @param name The column name
     */
    fun binary(name: String): Column<ByteArray> = registerColumn(name, BasicBinaryColumnType())

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
        is ColumnType -> {
            val autoIncSequence = idSeqName ?: "${tableName}_${name}_seq"
            this@cloneWithAutoInc.clone<Column<T>>(mapOf(Column<T>::columnType to AutoIncColumnType(columnType, autoIncSequence)))
        }
        else -> error("Unsupported column type for auto-increment $columnType")
    }

    /**
     * Make @receiver column an auto-increment to generate its values in a database.
     * Only integer and long columns supported.
     * Some databases like a PostgreSQL supports auto-increment via sequences.
     * In that case you should provide a name with [idSeqName] param and Exposed will create a sequence for you.
     * If you already have a sequence in a database just use its name in [idSeqName].
     *
     * @param idSeqName an optional parameter to provide a sequence name
     */
    fun <N:Any> Column<N>.autoIncrement(idSeqName: String? = null): Column<N> = cloneWithAutoInc(idSeqName).apply {
        replaceColumn(this@autoIncrement, this)
    }

    /**
     * Make @receiver column an auto-increment to generate its values in a database.
     * Only integer and long columns supported.
     * Some databases like a PostgreSQL supports auto-increment via sequences.
     * In that case you should provide a name with [idSeqName] param and Exposed will create a sequence for you.
     * If you already have a sequence in a database just use its name in [idSeqName].
     *
     * @param idSeqName an optional parameter to provide a sequence name
     */
    fun <N:Comparable<N>> Column<EntityID<N>>.autoinc(idSeqName: String? = null): Column<EntityID<N>> = cloneWithAutoInc(idSeqName).apply {
        replaceColumn(this@autoinc, this)
    }

    /**
     * UUID column will auto generate its value on a client side just before an insert
     */
    fun Column<UUID>.autoGenerate(): Column<UUID> = this.clientDefault { UUID.randomUUID() }

    /**
     * Create reference from a @receiver column to [ref] column with [onDelete] and [onUpdate] options.
     * [onDelete] and [onUpdate] options describes behavior on how links between tables will be checked in case of deleting or changing corespoding columns' values.
     * Such relationship will be represented as FOREIGN KEY constraint on a table creation.
     *
     * @receiver A column from current table where reference values will be stored
     * @param ref A column from another table which will be used as a "parent".
     * @param onDelete Optional reference option for cases when linked row from a parent table will be deleted. See [ReferenceOption] documentation for details.
     * @param onUpdate Optional reference option for cases when value in a referenced column had changed. See [ReferenceOption] documentation for details.
     */
    fun <T:Comparable<T>, S: T, C:Column<S>> C.references(ref: Column<T>, onDelete: ReferenceOption? = null, onUpdate: ReferenceOption? = null): C = apply {
        referee = ref
        this.onUpdate = onUpdate
        this.onDelete = onDelete
    }

    /**
     * Create reference from a @receiver column to [ref] column with [onDelete] and [onUpdate] options.
     * [onDelete] and [onUpdate] options describes behavior on how links between tables will be checked in case of deleting or changing corespoding columns' values.
     * Such relationship will be represented as FOREIGN KEY constraint on a table creation.
     *
     * @receiver A column from current table where reference values will be stored
     * @param ref A column from another table which will be used as a "parent".
     * @param onDelete Optional reference option for cases when linked row from a parent table will be deleted. See [ReferenceOption] documentation for details.
     * @param onUpdate Optional reference option for cases when value in a referenced column had changed. See [ReferenceOption] documentation for details.
     */
    @JvmName("referencesById")
    fun <T:Comparable<T>, S: T, C:Column<S>> C.references(ref: Column<EntityID<T>>, onDelete: ReferenceOption? = null, onUpdate: ReferenceOption? = null): C = apply {
        referee = ref
        this.onUpdate = onUpdate
        this.onDelete = onDelete
    }

    /**
     * Create reference from a @receiver column to [ref] column.
     *
     * It's a short infix version of [references] function with default onDelete and onUpdate behavior.
     *
     * @receiver A column from current table where reference values will be stored
     * @param ref A column from another table which will be used as a "parent".
     * @see [references]
     */
    infix fun <T:Comparable<T>, S: T, C:Column<S>> C.references(ref: Column<T>): C = references(ref, null, null)

    fun <T:Comparable<T>> reference(name: String, foreign: IdTable<T>,
                                    onDelete: ReferenceOption? = null, onUpdate: ReferenceOption? = null): Column<EntityID<T>> =
            entityId(name, foreign).references(foreign.id, onDelete, onUpdate)

    fun <T:Comparable<T>> reference(name: String, refColumn: Column<T>,
                                    onDelete: ReferenceOption? = null, onUpdate: ReferenceOption? = null): Column<T> {
        val originalType = (refColumn.columnType as? EntityIDColumnType<*>)?.idColumn?.columnType ?: refColumn.columnType
        val column = Column<T>(this, name, originalType.cloneAsBaseType()).references(refColumn, onDelete, onUpdate)
        this._columns.addColumn(column)
        return column
    }

    fun <T:Comparable<T>> optReference(name: String, foreign: IdTable<T>,
                                       onDelete: ReferenceOption? = null, onUpdate: ReferenceOption? = null): Column<EntityID<T>?> =
            entityId(name, foreign).references(foreign.id, onDelete, onUpdate).nullable()

    fun <T:Comparable<T>> optReference(name: String, refColumn: Column<T>,
                                    onDelete: ReferenceOption? = null, onUpdate: ReferenceOption? = null): Column<T?> =
         Column<T>(this, name, refColumn.columnType.cloneAsBaseType()).references(refColumn, onDelete, onUpdate).nullable()

    fun <T:Any> Column<T>.nullable(): Column<T?> {
        val newColumn = Column<T?> (table, name, columnType)
        newColumn.referee = referee
        newColumn.onUpdate = onUpdate.takeIf { it != currentDialectIfAvailable?.defaultReferenceOption }
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

    fun index(isUnique: Boolean = false, vararg columns: Column<*>) {
        index(null, isUnique, *columns)
    }

    fun index(customIndexName:String? = null, isUnique: Boolean = false, vararg columns: Column<*>) {
        (indices as MutableList<Index>).add(Index(columns.toList(), isUnique, customIndexName))
    }

    fun<T> Column<T>.index(customIndexName:String? = null, isUnique: Boolean = false) : Column<T> = apply {
        table.index(customIndexName, isUnique, this)
    }

    fun<T> Column<T>.uniqueIndex(customIndexName:String? = null) : Column<T> = index(customIndexName,true)

    fun uniqueIndex(vararg columns: Column<*>) {
        index(null,true, *columns)
    }

    fun uniqueIndex(customIndexName:String? = null, vararg columns: Column<*>) {
        index(customIndexName,true, *columns)
    }

    /**
     * Creates a check constraint in this column.
     * @param name The name to identify the constraint, optional. Must be **unique** (case-insensitive) to this table, otherwise, the constraint will
     * not be created. All names are [trimmed][String.trim], blank names are ignored and the database engine decides the default name.
     * @param op The expression against which the newly inserted values will be compared.
     */
    fun <T> Column<T>.check(name: String = "", op: SqlExpressionBuilder.(Column<T>) -> Op<Boolean>) = apply {
        table.checkConstraints.takeIf { name.isEmpty() || it.none { it.first.equals(name, true) } }?.add(name to SqlExpressionBuilder.op(this))
                ?: exposedLogger.warn("A CHECK constraint with name '$name' was ignored because there is already one with that name")
    }

    /**
     * Creates a check constraint in this table.
     * @param name The name to identify the constraint, optional. Must be **unique** (case-insensitive) to this table, otherwise, the constraint will
     * not be created. All names are [trimmed][String.trim], blank names are ignored and the database engine decides the default name.
     * @param op The expression against which the newly inserted values will be compared.
     */
    fun check(name: String = "", op: SqlExpressionBuilder.() -> Op<Boolean>) {
        checkConstraints.takeIf { name.isEmpty() || it.none { it.first.equals(name, true) } }?.add(name to SqlExpressionBuilder.op())
                ?: exposedLogger.warn("A CHECK constraint with name '$name' was ignored because there is already one with that name")
    }

    val ddl: List<String>
        get() = createStatement()

    override fun createStatement(): List<String> {
        val seqDDL = autoIncColumn?.autoIncSeqName?.let {
            Seq(it).createStatement()
        }.orEmpty()

        val addForeignKeysInAlterPart = SchemaUtils.checkCycle(this) && currentDialect !is SQLiteDialect

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

                if (!addForeignKeysInAlterPart) {
                    columns.filter { it.referee != null }.takeIf { it.isNotEmpty() }?.let { references ->
                        references.joinTo(this, prefix = ", ", separator = ", ") { ForeignKeyConstraint.from(it).foreignKeyPart }
                    }
                }

                if (checkConstraints.isNotEmpty()) {
                    checkConstraints.asSequence().mapIndexed { index, (name, op) ->
                        val resolvedName = name.takeIf { it.isNotBlank() } ?: "check_${tableName}_$index"
                        CheckConstraint.from(this@Table, resolvedName, op).checkPart
                    }.joinTo(this, prefix = ",", separator = ",")
                }

                append(")")
            }
        }

        val constraintSQL = if (addForeignKeysInAlterPart) {
            columns.filter { it.referee != null }.flatMap {
                ForeignKeyConstraint.from(it).createStatement()
            }
        } else emptyList()

        return seqDDL + createTableDDL + constraintSQL
    }

    internal fun primaryKeyConstraint(): String? {
        val pkey = columns.filter { it.indexInPK != null }.sortedWith(compareBy({ !it.columnType.isAutoInc }, { it.indexInPK }))

        if (pkey.isNotEmpty()) {
            val tr = TransactionManager.current()
            val constraint = tr.db.identifierManager.cutIfNecessaryAndQuote("pk_$tableName")
            return pkey.joinToString(
                    prefix = "CONSTRAINT $constraint PRIMARY KEY (", postfix = ")") {
                tr.identity(it)
            }
        }
        return null
    }

    override fun dropStatement() : List<String> {
        val dropTableDDL = buildString {
            append("DROP TABLE ")
            if (currentDialect.supportsIfNotExists) {
                append("IF EXISTS ")
            }
            append(TransactionManager.current().identity(this@Table))
            if (currentDialectIfAvailable is OracleDialect) {
                append(" CASCADE CONSTRAINTS")
            }

            if (currentDialectIfAvailable is PostgreSQLDialect && SchemaUtils.checkCycle(this@Table)) {
                append(" CASCADE")
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

    private fun <T> MutableList<Column<*>>.addColumn(column: Column<T>) {
        if (this.any { it.name == column.name }) {
            throw DuplicateColumnException(column.name, tableName)
        } else {
            this.add(column)
        }
    }
}

data class Seq(private val name: String) {
    private val identifier get() = TransactionManager.current().db.identifierManager.cutIfNecessaryAndQuote(name)
    fun createStatement() = listOf("CREATE SEQUENCE $identifier")
    fun dropStatement() = listOf("DROP SEQUENCE $identifier")
}

fun ColumnSet.targetTables(): List<Table> = when (this) {
    is Alias<*> -> listOf(this.delegate)
    is QueryAlias -> this.query.set.source.targetTables()
    is Table -> listOf(this)
    is Join -> this.table.targetTables() + this.joinParts.flatMap { it.joinPart.targetTables() }
    else -> error("No target provided for update")
}

