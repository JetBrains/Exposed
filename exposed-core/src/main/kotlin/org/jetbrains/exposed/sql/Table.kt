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
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/** Pair of expressions used to match rows from two joined tables. */
typealias JoinCondition = Pair<Expression<*>, Expression<*>>

/**
 * Represents a set of expressions, contained in the given column set.
 */
interface FieldSet {
    /** Return the column set that contains this field set. */
    val source: ColumnSet
    /** Returns the field of this field set. */
    val fields: List<Expression<*>>
}

/**
 * Represents a set of columns.
 */
abstract class ColumnSet : FieldSet {
    override val source: ColumnSet get() = this
    /** Returns the columns of this column set. */
    abstract val columns: List<Column<*>>
    override val fields: List<Expression<*>> get() = columns

    /** Appends the SQL representation of this column set to the specified [queryBuilder]. */
    abstract fun describe(s: Transaction, queryBuilder: QueryBuilder)

    /**
     * Creates a join relation with [otherTable].
     * When all joining options are absent Exposed will try to resolve referencing columns by itself.
     *
     * @param otherTable [ColumnSet] to join with.
     * @param joinType See [JoinType] for available options.
     * @param onColumn The column from a current [ColumnSet], may be skipped then [additionalConstraint] will be used.
     * @param otherColumn The column from an [otherTable], may be skipped then [additionalConstraint] will be used.
     * @param additionalConstraint The condition to join which will be placed in ON part of SQL query.
     *
     * @throws IllegalStateException If join could not be prepared. See exception message for more details.
     */
    abstract fun join(
        otherTable: ColumnSet,
        joinType: JoinType,
        onColumn: Expression<*>? = null,
        otherColumn: Expression<*>? = null,
        additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null
    ): Join

    /** Creates an inner join relation with [otherTable]. */
    abstract fun innerJoin(otherTable: ColumnSet): Join

    /** Creates a left outer join relation with [otherTable]. */
    abstract fun leftJoin(otherTable: ColumnSet): Join

    /** Creates a right outer join relation with [otherTable]. */
    abstract fun rightJoin(otherTable: ColumnSet): Join

    /** Creates a full outer join relation with [otherTable]. */
    abstract fun fullJoin(otherTable: ColumnSet): Join

    /** Creates a cross join relation with [otherTable]. */
    abstract fun crossJoin(otherTable: ColumnSet): Join

    /** Specifies a subset of [columns] of this [ColumnSet]. */
    fun slice(vararg columns: Expression<*>): FieldSet = Slice(this, columns.distinct())

    /** Specifies a subset of [columns] of this [ColumnSet]. */
    fun slice(columns: List<Expression<*>>): FieldSet = Slice(this, columns.distinct())
}

/** Creates an inner join relation with [otherTable] using [onColumn] and [otherColumn] as the join condition. */
fun <C1 : ColumnSet, C2 : ColumnSet> C1.innerJoin(
    otherTable: C2,
    onColumn: C1.() -> Expression<*>,
    otherColumn: C2.() -> Expression<*>
): Join = join(otherTable, JoinType.INNER, onColumn(this), otherColumn(otherTable))

/** Creates a left outer join relation with [otherTable] using [onColumn] and [otherColumn] as the join condition. */
fun <C1 : ColumnSet, C2 : ColumnSet> C1.leftJoin(
    otherTable: C2,
    onColumn: C1.() -> Expression<*>,
    otherColumn: C2.() -> Expression<*>
): Join = join(otherTable, JoinType.LEFT, onColumn(), otherTable.otherColumn())

/** Creates a right outer join relation with [otherTable] using [onColumn] and [otherColumn] as the join condition. */
fun <C1 : ColumnSet, C2 : ColumnSet> C1.rightJoin(
    otherTable: C2,
    onColumn: C1.() -> Expression<*>,
    otherColumn: C2.() -> Expression<*>
): Join = join(otherTable, JoinType.RIGHT, onColumn(), otherTable.otherColumn())

/** Creates a full outer join relation with [otherTable] using [onColumn] and [otherColumn] as the join condition. */
fun <C1 : ColumnSet, C2 : ColumnSet> C1.fullJoin(
    otherTable: C2,
    onColumn: C1.() -> Expression<*>,
    otherColumn: C2.() -> Expression<*>
): Join = join(otherTable, JoinType.FULL, onColumn(), otherTable.otherColumn())

/** Creates a cross join relation with [otherTable] using [onColumn] and [otherColumn] as the join condition. */
fun <C1 : ColumnSet, C2 : ColumnSet> C1.crossJoin(
    otherTable: C2,
    onColumn: C1.() -> Expression<*>,
    otherColumn: C2.() -> Expression<*>
): Join = join(otherTable, JoinType.CROSS, onColumn(), otherTable.otherColumn())

/**
 * Represents a subset of [fields] from a given [source].
 */
class Slice(override val source: ColumnSet, override val fields: List<Expression<*>>) : FieldSet

/**
 * Represents column set join types.
 */
enum class JoinType {
    /** Inner join. */
    INNER,
    /** Left outer join. */
    LEFT,
    /** Right outer join. */
    RIGHT,
    /** Full outer join. */
    FULL,
    /** Cross join. */
    CROSS
}

/**
 * Represents a join relation between multiple column sets.
 */
class Join(
    /** The column set to which others will be joined. */
    val table: ColumnSet
) : ColumnSet() {

    override val columns: List<Column<*>> get() = joinParts.flatMapTo(table.columns.toMutableList()) { it.joinPart.columns }

    internal val joinParts: MutableList<JoinPart> = mutableListOf()

    constructor(
        table: ColumnSet,
        otherTable: ColumnSet,
        joinType: JoinType = JoinType.INNER,
        onColumn: Expression<*>? = null,
        otherColumn: Expression<*>? = null,
        additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null
    ) : this(table) {
        val new = if (onColumn != null && otherColumn != null) {
            join(otherTable, joinType, onColumn, otherColumn, additionalConstraint)
        } else {
            join(otherTable, joinType, additionalConstraint)
        }
        joinParts.addAll(new.joinParts)
    }

    override fun describe(s: Transaction, queryBuilder: QueryBuilder): Unit = queryBuilder {
        table.describe(s, this)
        for (p in joinParts) {
            append(" ${p.joinType} JOIN ")
            val isJoin = p.joinPart is Join
            if (isJoin) {
                append("(")
            }
            p.joinPart.describe(s, this)
            if (isJoin) {
                append(")")
            }
            if (p.joinType != JoinType.CROSS) {
                append(" ON ")
                p.conditions.appendTo(this, " AND ") { (pkColumn, fkColumn) -> append(pkColumn, " = ", fkColumn) }
                if (p.additionalConstraint != null) {
                    if (p.conditions.isNotEmpty()) {
                        append(" AND ")
                    }
                    append(" (")
                    append(SqlExpressionBuilder.(p.additionalConstraint)())
                    append(")")
                }
            }
        }
    }

    override fun join(
        otherTable: ColumnSet,
        joinType: JoinType,
        onColumn: Expression<*>?,
        otherColumn: Expression<*>?,
        additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)?
    ): Join {
        val cond = if (onColumn != null && otherColumn != null) {
            listOf(JoinCondition(onColumn, otherColumn))
        } else {
            emptyList()
        }
        return join(otherTable, joinType, cond, additionalConstraint)
    }

    override infix fun innerJoin(otherTable: ColumnSet): Join = join(otherTable, JoinType.INNER)

    override infix fun leftJoin(otherTable: ColumnSet): Join = join(otherTable, JoinType.LEFT)

    override infix fun rightJoin(otherTable: ColumnSet): Join = join(otherTable, JoinType.RIGHT)

    override infix fun fullJoin(otherTable: ColumnSet): Join = join(otherTable, JoinType.FULL)

    override infix fun crossJoin(otherTable: ColumnSet): Join = join(otherTable, JoinType.CROSS)

    private fun join(
        otherTable: ColumnSet,
        joinType: JoinType = JoinType.INNER,
        additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null
    ): Join {
        val fkKeys = findKeys(this, otherTable) ?: findKeys(otherTable, this) ?: emptyList()
        return when {
            joinType != JoinType.CROSS && fkKeys.isEmpty() && additionalConstraint == null -> {
                error("Cannot join with $otherTable as there is no matching primary key/foreign key pair and constraint missing")
            }
            fkKeys.any { it.second.size > 1 } && additionalConstraint == null -> {
                val references = fkKeys.joinToString(" & ") { "${it.first} -> ${it.second.joinToString()}" }
                error("Cannot join with $otherTable as there is multiple primary key <-> foreign key references.\n$references")
            }
            else -> {
                val cond = fkKeys.filter { it.second.size == 1 }.map { it.first to it.second.single() }
                join(otherTable, joinType, cond, additionalConstraint)
            }
        }
    }

    private fun join(
        otherTable: ColumnSet,
        joinType: JoinType,
        cond: List<JoinCondition>,
        additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)?
    ): Join = Join(table).also {
        it.joinParts.addAll(this.joinParts)
        it.joinParts.add(JoinPart(joinType, otherTable, cond, additionalConstraint))
    }

    private fun findKeys(a: ColumnSet, b: ColumnSet): List<Pair<Column<*>, List<Column<*>>>>? = a.columns
        .map { a_pk -> a_pk to b.columns.filter { it.referee == a_pk } }
        .filter { it.second.isNotEmpty() }
        .takeIf { it.isNotEmpty() }

    /** Return `true` if the specified [table] is already in this join, `false` otherwise. */
    fun alreadyInJoin(table: Table): Boolean = joinParts.any { it.joinPart == table }

    internal class JoinPart(
        val joinType: JoinType,
        val joinPart: ColumnSet,
        val conditions: List<JoinCondition>,
        val additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null
    ) {
        init {
            require(joinType == JoinType.CROSS || conditions.isNotEmpty() || additionalConstraint != null) { "Missing join condition on $${this.joinPart}" }
        }
    }
}

/**
 * Base class for any simple table.
 *
 * If you want to reference your table use [IdTable] instead.
 *
 * @param name Table name, by default name will be resolved from a class name with "Table" suffix removed (if present)
 */
open class Table(name: String = "") : ColumnSet(), DdlAware {
    /** Returns the table name. */
    open val tableName: String = if (name.isNotEmpty()) name else this.javaClass.simpleName.removeSuffix("Table")
    internal val tableNameWithoutScheme: String get() = tableName.substringAfter(".")

    private val _columns = mutableListOf<Column<*>>()
    /** Returns all the columns defined on the table. */
    override val columns: List<Column<*>> get() = _columns

    /** Returns the first auto-increment column on the table. */
    val autoIncColumn: Column<*>? get() = columns.firstOrNull { it.columnType.isAutoInc }

    private val _indices = mutableListOf<Index>()
    /** Returns all indices declared on the table. */
    val indices: List<Index> get() = _indices

    private val checkConstraints = mutableListOf<Pair<String, Op<Boolean>>>()

    /**
     * Returns the table name in proper case.
     * Should be called within transaction or default [tableName] will be returned.
     */
    fun nameInDatabaseCase(): String = tableName.inProperCase()

    override fun describe(s: Transaction, queryBuilder: QueryBuilder): Unit = queryBuilder { append(s.identity(this@Table)) }

    // Join operations

    override fun join(
        otherTable: ColumnSet,
        joinType: JoinType,
        onColumn: Expression<*>?,
        otherColumn: Expression<*>?,
        additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)?
    ): Join = Join(this, otherTable, joinType, onColumn, otherColumn, additionalConstraint)

    override infix fun innerJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.INNER)

    override infix fun leftJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.LEFT)

    override infix fun rightJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.RIGHT)

    override infix fun fullJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.FULL)

    override infix fun crossJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.CROSS)

    // Column registration

    /** Adds a column of the specified [type] and with the specified [name] to the table. */
    fun <T> registerColumn(name: String, type: IColumnType): Column<T> = Column<T>(this, name, type).also { _columns.addColumn(it) }

    /**
     * Replaces the specified [oldColumn] with the specified [newColumn] in the table.
     * Mostly used internally by the library.
     */
    fun <TColumn : Column<*>> replaceColumn(oldColumn: Column<*>, newColumn: TColumn): TColumn {
        _columns.remove(oldColumn)
        _columns.addColumn(newColumn)
        return newColumn
    }

    private fun MutableList<Column<*>>.addColumn(column: Column<*>) {
        if (this.any { it.name == column.name }) {
            throw DuplicateColumnException(column.name, tableName)
        }
        this.add(column)
    }

    // Primary keys

    internal fun isCustomPKNameDefined(): Boolean = primaryKey?.let { it.name != "pk_$tableName" } == true

    /**
     * Represents a primary key composed by the specified [columns], and with the specified [name].
     * If no name is specified, the table name with the "pk_" prefix will be used instead.
     *
     * @sample org.jetbrains.exposed.sql.tests.demo.sql.Users
     */
    inner class PrimaryKey(
        /** Returns the columns that compose the primary key. */
        vararg val columns: Column<*>,
        /** Returns the name of the primary key. */
        val name: String = "pk_$tableName"
    ) {
        init {
            checkMultipleDeclaration()
            for (column in columns) column.markPrimaryKey()
        }

        /**
         * Initialize PrimaryKey class with columns defined using [primaryKey] method
         *
         * This constructor must be removed when [primaryKey] method is no longer supported.
         */
        internal constructor(columns: List<Column<*>>) : this(*columns.toTypedArray())

        /** Marks the receiver column as an element of primary key. */
        private fun Column<*>.markPrimaryKey() {
            indexInPK = table.columns.count { it.indexInPK != null } + 1
        }

        /** Check if both old and new declarations of primary key are defined.
         *
         * Remove columns from primary key to take columns declared in PrimaryKey class instead.
         * Log an error.
         * This function must be removed when [primaryKey] method is no longer supported.
         */
        private fun checkMultipleDeclaration() {
            val table = this@Table
            if (table.columns.any { it.indexInPK != null }) {
                removeOldPrimaryKey()
                exposedLogger.error(
                    "Confusion between multiple declarations of primary key on ${table.tableName}. " +
                            "Use only override val primaryKey=PrimaryKey() declaration."
                )
            }
        }

        /** This function must be removed when [primaryKey] method is no longer supported. */
        private fun removeOldPrimaryKey() = columns.filter { it.indexInPK != null }.forEach { it.indexInPK = null }
    }

    /**
     * Returns the primary key of the table if present, `null` otherwise.
     *
     * Currently, it is initialized with existing keys defined by [Column.primaryKey] function for a backward compatibility,
     * but you have to define it explicitly by overriding that val instead.
     */
    open val primaryKey: PrimaryKey? by lazy { getPrimaryKeyColumns()?.let(::PrimaryKey) }

    /** Returns the list of columns in the primary key if present. */
    private fun getPrimaryKeyColumns(): List<Column<*>>? = columns
        .filter { it.indexInPK != null }
        .sortedWith(compareBy({ !it.columnType.isAutoInc }, { it.indexInPK }))
        .takeIf { it.isNotEmpty() }

    /**
     * Mark @receiver column as primary key.
     *
     * When you define multiple primary keys on a table it will create composite key.
     * Order of columns in a primary key will be the same as order of the columns in a table mapping from top to bottom.
     * If you desire to change the order only in a primary key provide [indx] parameter.
     *
     * @param indx An optional column index in a primary key
     */
    @Deprecated(
        "This function will be no longer supported. Please use the new declarations of primary key by " +
                "overriding the primaryKey property in the current table. " +
                "Example : object TableName : Table() { override val primaryKey = PrimaryKey(column1, column2, name = \"CustomPKConstraintName\") }"
    )
    fun <T> Column<T>.primaryKey(indx: Int? = null): Column<T> = apply {
        require(indx == null || table.columns.none { it.indexInPK == indx }) { "Table $tableName already contains PK at $indx" }
        indexInPK = indx ?: table.columns.count { it.indexInPK != null } + 1
    }

    // EntityID columns

    /** Converts the @receiver column to an [EntityID] column. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Comparable<T>> Column<T>.entityId(): Column<EntityID<T>> {
        val newColumn = Column<EntityID<T>>(table, name, EntityIDColumnType(this)).also {
            it.indexInPK = indexInPK
            it.defaultValueFun = defaultValueFun?.let { { EntityIDFunctionProvider.createEntityID(it(), table as IdTable<T>) } }
        }
        return replaceColumn(this, newColumn)
    }

    /** Creates an [EntityID] column, with the specified [name], for storing the same objects as the specified [originalColumn]. */
    fun <ID : Comparable<ID>> entityId(name: String, originalColumn: Column<ID>): Column<EntityID<ID>> {
        val columnTypeCopy = originalColumn.columnType.cloneAsBaseType()
        val answer = Column<EntityID<ID>>(this, name, EntityIDColumnType(Column<ID>(originalColumn.table, name, columnTypeCopy)))
        _columns.addColumn(answer)
        return answer
    }

    /** Creates an [EntityID] column, with the specified [name], for storing the identifier of the specified [table]. */
    @Suppress("UNCHECKED_CAST")
    fun <ID : Comparable<ID>> entityId(name: String, table: IdTable<ID>): Column<EntityID<ID>> {
        val originalColumn = (table.id.columnType as EntityIDColumnType<*>).idColumn as Column<ID>
        return entityId(name, originalColumn)
    }

    // Numeric columns

    /** Creates a numeric column, with the specified [name], for storing 2-byte integers. */
    fun short(name: String): Column<Short> = registerColumn(name, ShortColumnType())

    /** Creates a numeric column, with the specified [name], for storing 4-byte integers. */
    fun integer(name: String): Column<Int> = registerColumn(name, IntegerColumnType())

    /** Creates a numeric column, with the specified [name], for storing 8-byte integers. */
    fun long(name: String): Column<Long> = registerColumn(name, LongColumnType())

    /** Creates a numeric column, with the specified [name], for storing 4-byte (single precision) floating-point numbers. */
    fun float(name: String): Column<Float> = registerColumn(name, FloatColumnType())

    /** Creates a numeric column, with the specified [name], for storing 8-byte (double precision) floating-point numbers. */
    fun double(name: String): Column<Double> = registerColumn(name, DoubleColumnType())

    /**
     * Creates a numeric column, with the specified [name], for storing numbers with the specified [precision] and [scale].
     *
     * To store the decimal `123.45`, [precision] would have to be set to 5 (as there are five digits in total) and
     * [scale] to 2 (as there are two digits behind the decimal point).
     *
     * @param name Name of the column.
     * @param precision Total count of significant digits in the whole number, that is, the number of digits to both sides of the decimal point.
     * @param scale Count of decimal digits in the fractional part.
     */
    fun decimal(name: String, precision: Int, scale: Int): Column<BigDecimal> = registerColumn(name, DecimalColumnType(precision, scale))

    // Character columns

    /** Creates a character column, with the specified [name], for storing single characters. */
    fun char(name: String): Column<Char> = registerColumn(name, CharacterColumnType())

    /**
     * Creates a character column, with the specified [name], for storing strings with the specified maximum [length] using the specified text [collate] type.
     * If no collate type is specified then the database default is used.
     */
    fun varchar(name: String, length: Int, collate: String? = null): Column<String> = registerColumn(name, VarCharColumnType(length, collate))

    /**
     * Creates a character column, with the specified [name], for storing strings of arbitrary length using the specified [collate] type.
     * If no collate type is specified then the database default is used.
     */
    fun text(name: String, collate: String? = null): Column<String> = registerColumn(name, TextColumnType(collate))

    // Binary columns

    /**
     * Creates a binary column, with the specified [name], for storing byte arrays of arbitrary size.
     *
     * **Note:** This function is only supported by Oracle and PostgeSQL dialects, for the rest please specify a length.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.DDLTests.testBinaryWithoutLength
     */
    fun binary(name: String): Column<ByteArray> = registerColumn(name, BasicBinaryColumnType())

    /**
     * Creates a binary column, with the specified [name], for storing byte arrays with the specified maximum [length].
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.DDLTests.testBinary
     */
    fun binary(name: String, length: Int): Column<ByteArray> = registerColumn(name, BinaryColumnType(length))

    /**
     * Creates a binary column, with the specified [name], for storing BLOBs.
     *
     * @sample org.jetbrains.exposed.sql.tests.shared.DDLTests.testBlob
     */
    fun blob(name: String): Column<ExposedBlob> = registerColumn(name, BlobColumnType())

    /** Creates a binary column, with the specified [name], for storing UUIDs. */
    fun uuid(name: String): Column<UUID> = registerColumn(name, UUIDColumnType())

    // Boolean columns

    /** Creates a column, with the specified [name], for storing boolean values. */
    fun bool(name: String): Column<Boolean> = registerColumn(name, BooleanColumnType())

    // Enumeration columns

    /** Creates an enumeration column, with the specified [name], for storing enums of type [klass] by their ordinal. */
    fun <T : Enum<T>> enumeration(name: String, klass: KClass<T>): Column<T> = registerColumn(name, EnumerationColumnType(klass))

    /**
     * Creates an enumeration column, with the specified [name], for storing enums of type [klass] by their name.
     * With the specified maximum [length] for each name value.
     */
    fun <T : Enum<T>> enumerationByName(name: String, length: Int, klass: KClass<T>): Column<T> = registerColumn(name, EnumerationNameColumnType(klass, length))

    /**
     * Creates an enumeration column with custom SQL type.
     * The main usage is to use a database specific type.
     *
     * See [https://github.com/JetBrains/Exposed/wiki/DataTypes#how-to-use-database-enum-types] for more details.
     *
     * @param name The column name
     * @param sql A SQL definition for the column
     * @param fromDb A lambda to convert a value received from a database to an enumeration instance
     * @param toDb A lambda to convert an enumeration instance to a value which will be stored to a database
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Enum<T>> customEnumeration(
        name: String,
        sql: String? = null,
        fromDb: (Any) -> T,
        toDb: (T) -> Any
    ): Column<T> = registerColumn(name, object : StringColumnType() {
        override fun sqlType(): String = sql ?: error("Column $name should exists in database ")
        override fun valueFromDB(value: Any): T = if (value::class.isSubclassOf(Enum::class)) value as T else fromDb(value)
        override fun notNullValueToDB(value: Any): Any = toDb(value as T)
    })

    // Auto-generated values

    /**
     * Make @receiver column an auto-increment to generate its values in a database.
     * Only integer and long columns supported.
     * Some databases like a PostgreSQL supports auto-increment via sequences.
     * In that case you should provide a name with [idSeqName] param and Exposed will create a sequence for you.
     * If you already have a sequence in a database just use its name in [idSeqName].
     *
     * @param idSeqName an optional parameter to provide a sequence name
     */
    fun <N : Any> Column<N>.autoIncrement(idSeqName: String? = null): Column<N> =
        cloneWithAutoInc(idSeqName).also { replaceColumn(this, it) }

    /**
     * Make @receiver column an auto-increment to generate its values in a database.
     * Only integer and long columns supported.
     * Some databases like a PostgreSQL supports auto-increment via sequences.
     * In that case you should provide a name with [idSeqName] param and Exposed will create a sequence for you.
     * If you already have a sequence in a database just use its name in [idSeqName].
     *
     * @param idSeqName an optional parameter to provide a sequence name
     */
    fun <N : Comparable<N>> Column<EntityID<N>>.autoinc(idSeqName: String? = null): Column<EntityID<N>> =
        cloneWithAutoInc(idSeqName).also { replaceColumn(this, it) }

    /** Sets the default value for this column in the database side. */
    fun <T : Any> Column<T>.default(defaultValue: T): Column<T> = apply {
        dbDefaultValue = with(SqlExpressionBuilder) { asLiteral(defaultValue) }
        defaultValueFun = { defaultValue }
    }

    /** Sets the default value for this column in the database side. */
    fun <T : Any> Column<T>.defaultExpression(defaultValue: Expression<T>): Column<T> = apply {
        dbDefaultValue = defaultValue
        defaultValueFun = null
    }

    /** Sets the default value for this column in the client side. */
    fun <T : Any> Column<T>.clientDefault(defaultValue: () -> T): Column<T> = apply {
        dbDefaultValue = null
        defaultValueFun = defaultValue
    }

    /** UUID column will auto generate its value on a client side just before an insert. */
    fun Column<UUID>.autoGenerate(): Column<UUID> = clientDefault { UUID.randomUUID() }

    // Column references

    /**
     * Create reference from a @receiver column to [ref] column.
     *
     * It's a short infix version of [references] function with default onDelete and onUpdate behavior.
     *
     * @receiver A column from current table where reference values will be stored
     * @param ref A column from another table which will be used as a "parent".
     * @see [references]
     */
    infix fun <T : Comparable<T>, S : T, C : Column<S>> C.references(ref: Column<T>): C = references(ref, null, null, null)

    /**
     * Create reference from a @receiver column to [ref] column with [onDelete], [onUpdate], and [fkName] options.
     * [onDelete] and [onUpdate] options describes behavior on how links between tables will be checked in case of deleting or changing corresponding columns' values.
     * Such relationship will be represented as FOREIGN KEY constraint on a table creation.
     *
     * @receiver A column from current table where reference values will be stored
     * @param ref A column from another table which will be used as a "parent".
     * @param onDelete Optional reference option for cases when linked row from a parent table will be deleted. See [ReferenceOption] documentation for details.
     * @param onUpdate Optional reference option for cases when value in a referenced column had changed. See [ReferenceOption] documentation for details.
     * @param fkName Optional foreign key constraint name.
     */
    fun <T : Comparable<T>, S : T, C : Column<S>> C.references(
        ref: Column<T>,
        onDelete: ReferenceOption? = null,
        onUpdate: ReferenceOption? = null,
        fkName: String? = null
    ): C = apply {
        this.foreignKey = ForeignKeyConstraint(
                target = ref,
                from = this,
                onUpdate = onUpdate,
                onDelete = onDelete,
                name = fkName
        )
    }

    /**
     * Create reference from a @receiver column to [ref] column with [onDelete], [onUpdate], and [fkName] options.
     * [onDelete] and [onUpdate] options describes behavior on how links between tables will be checked in case of deleting or changing corresponding columns' values.
     * Such relationship will be represented as FOREIGN KEY constraint on a table creation.
     *
     * @receiver A column from current table where reference values will be stored
     * @param ref A column from another table which will be used as a "parent".
     * @param onDelete Optional reference option for cases when linked row from a parent table will be deleted. See [ReferenceOption] documentation for details.
     * @param onUpdate Optional reference option for cases when value in a referenced column had changed. See [ReferenceOption] documentation for details.
     * @param fkName Optional foreign key constraint name.
     */
    @JvmName("referencesById")
    fun <T : Comparable<T>, S : T, C : Column<S>> C.references(
        ref: Column<EntityID<T>>,
        onDelete: ReferenceOption? = null,
        onUpdate: ReferenceOption? = null,
        fkName: String? = null
    ): C = apply {
        this.foreignKey = ForeignKeyConstraint(
                target = ref,
                from = this,
                onUpdate = onUpdate,
                onDelete = onDelete,
                name = fkName
        )
    }

    /**
     * Creates a column with the specified [name] with a reference to the [refColumn] column and with [onDelete], [onUpdate], and [fkName] options.
     * [onDelete] and [onUpdate] options describes behavior on how links between tables will be checked in case of deleting or changing corresponding columns' values.
     * Such relationship will be represented as FOREIGN KEY constraint on a table creation.
     *
     * @param name Name of the column.
     * @param refColumn A column from another table which will be used as a "parent".
     * @param onDelete Optional reference option for cases when linked row from a parent table will be deleted.
     * @param onUpdate Optional reference option for cases when value in a referenced column had changed.
     * @param fkName Optional foreign key constraint name.
     *
     * @see ReferenceOption
     */
    fun <T : Comparable<T>> reference(
        name: String,
        refColumn: Column<T>,
        onDelete: ReferenceOption? = null,
        onUpdate: ReferenceOption? = null,
        fkName: String? = null
    ): Column<T> {
        val column = Column<T>(this, name, refColumn.columnType.cloneAsBaseType()).references(refColumn, onDelete, onUpdate, fkName)
        _columns.addColumn(column)
        return column
    }

    /**
     * Creates a column with the specified [name] with a reference to the [refColumn] column and with [onDelete], [onUpdate], and [fkName] options.
     * [onDelete] and [onUpdate] options describes behavior on how links between tables will be checked in case of deleting or changing corresponding columns' values.
     * Such relationship will be represented as FOREIGN KEY constraint on a table creation.
     *
     * @param name Name of the column.
     * @param refColumn A column from another table which will be used as a "parent".
     * @param onDelete Optional reference option for cases when linked row from a parent table will be deleted.
     * @param onUpdate Optional reference option for cases when value in a referenced column had changed.
     * @param fkName Optional foreign key constraint name.
     *
     * @see ReferenceOption
     */
    @Suppress("UNCHECKED_CAST")
    @JvmName("referenceByIdColumn")
    fun <T : Comparable<T>, E : EntityID<T>> reference(
        name: String,
        refColumn: Column<E>,
        onDelete: ReferenceOption? = null,
        onUpdate: ReferenceOption? = null,
        fkName: String? = null
    ): Column<E> {
        val entityIDColumn = entityId(name, (refColumn.columnType as EntityIDColumnType<T>).idColumn) as Column<E>
        return entityIDColumn.references(refColumn, onDelete, onUpdate, fkName)
    }

    /**
     * Creates a column with the specified [name] with a reference to the `id` column in [foreign] table and with [onDelete], [onUpdate], and [fkName] options.
     * [onDelete] and [onUpdate] options describes behavior on how links between tables will be checked in case of deleting or changing corresponding columns' values.
     * Such relationship will be represented as FOREIGN KEY constraint on a table creation.
     *
     * @param name Name of the column.
     * @param foreign A table with an `id` column which will be used as a "parent".
     * @param onDelete Optional reference option for cases when linked row from a parent table will be deleted.
     * @param onUpdate Optional reference option for cases when value in a referenced column had changed.
     * @param fkName Optional foreign key constraint name.
     *
     * @see ReferenceOption
     */
    fun <T : Comparable<T>> reference(
        name: String,
        foreign: IdTable<T>,
        onDelete: ReferenceOption? = null,
        onUpdate: ReferenceOption? = null,
        fkName: String? = null
    ): Column<EntityID<T>> = entityId(name, foreign).references(foreign.id, onDelete, onUpdate, fkName)

    /**
     * Creates a column with the specified [name] with an optional reference to the [refColumn] column with [onDelete], [onUpdate], and [fkName] options.
     * [onDelete] and [onUpdate] options describes behavior on how links between tables will be checked in case of deleting or changing corresponding columns' values.
     * Such relationship will be represented as FOREIGN KEY constraint on a table creation.
     *
     * @param name Name of the column.
     * @param refColumn A column from another table which will be used as a "parent".
     * @param onDelete Optional reference option for cases when linked row from a parent table will be deleted.
     * @param onUpdate Optional reference option for cases when value in a referenced column had changed.
     * @param fkName Optional foreign key constraint name.
     *
     * @see ReferenceOption
     */
    fun <T : Comparable<T>> optReference(
        name: String,
        refColumn: Column<T>,
        onDelete: ReferenceOption? = null,
        onUpdate: ReferenceOption? = null,
        fkName: String? = null
    ): Column<T?> = Column<T>(this, name, refColumn.columnType.cloneAsBaseType()).references(refColumn, onDelete, onUpdate, fkName).nullable()

    /**
     * Creates a column with the specified [name] with an optional reference to the [refColumn] column with [onDelete], [onUpdate], and [fkName] options.
     * [onDelete] and [onUpdate] options describes behavior on how links between tables will be checked in case of deleting or changing corresponding columns' values.
     * Such relationship will be represented as FOREIGN KEY constraint on a table creation.
     *
     * @param name Name of the column.
     * @param refColumn A column from another table which will be used as a "parent".
     * @param onDelete Optional reference option for cases when linked row from a parent table will be deleted.
     * @param onUpdate Optional reference option for cases when value in a referenced column had changed.
     * @param fkName Optional foreign key constraint name.
     *
     * @see ReferenceOption
     */
    @Suppress("UNCHECKED_CAST")
    @JvmName("optReferenceByIdColumn")
    fun <T : Comparable<T>, E : EntityID<T>> optReference(
        name: String,
        refColumn: Column<E>,
        onDelete: ReferenceOption? = null,
        onUpdate: ReferenceOption? = null,
        fkName: String? = null
    ): Column<E?> {
        val entityIdColumn = entityId(name, (refColumn.columnType as EntityIDColumnType<T>).idColumn) as Column<E>
        return entityIdColumn.references(refColumn, onDelete, onUpdate, fkName).nullable()
    }

    /**
     * Creates a column with the specified [name] with an optional reference to the `id` column in [foreign] table with [onDelete], [onUpdate], and [fkName] options.
     * [onDelete] and [onUpdate] options describes behavior on how links between tables will be checked in case of deleting or changing corresponding columns' values.
     * Such relationship will be represented as FOREIGN KEY constraint on a table creation.
     *
     * @param name Name of the column.
     * @param foreign A table with an `id` column which will be used as a "parent".
     * @param onDelete Optional reference option for cases when linked row from a parent table will be deleted.
     * @param onUpdate Optional reference option for cases when value in a referenced column had changed.
     * @param fkName Optional foreign key constraint name.
     *
     * @see ReferenceOption
     */
    fun <T : Comparable<T>> optReference(
        name: String,
        foreign: IdTable<T>,
        onDelete: ReferenceOption? = null,
        onUpdate: ReferenceOption? = null,
        fkName: String? = null
    ): Column<EntityID<T>?> = entityId(name, foreign).references(foreign.id, onDelete, onUpdate, fkName).nullable()

    // Miscellaneous

    /** Marks this column as nullable. */
    fun <T : Any> Column<T>.nullable(): Column<T?> {
        val newColumn = Column<T?>(table, name, columnType)
        newColumn.foreignKey = foreignKey
        newColumn.defaultValueFun = defaultValueFun
        @Suppress("UNCHECKED_CAST")
        newColumn.dbDefaultValue = dbDefaultValue as Expression<T?>?
        newColumn.columnType.nullable = true
        return replaceColumn(this, newColumn)
    }

    // Indices

    /**
     * Creates an index.
     *
     * @param columns Columns that compose the index.
     * @param isUnique Whether the index is unique or not.
     */
    fun index(isUnique: Boolean = false, vararg columns: Column<*>): Unit = index(null, isUnique, *columns)

    /**
     * Creates an index.
     *
     * @param customIndexName Name of the index.
     * @param columns Columns that compose the index.
     * @param isUnique Whether the index is unique or not.
     */
    fun index(customIndexName: String? = null, isUnique: Boolean = false, vararg columns: Column<*>) {
        _indices.add(Index(columns.toList(), isUnique, customIndexName))
    }

    /**
     * Creates an index composed by this column only.
     *
     * @param customIndexName Name of the index.
     * @param isUnique Whether the index is unique or not.
     */
    fun <T> Column<T>.index(customIndexName: String? = null, isUnique: Boolean = false): Column<T> = apply { table.index(customIndexName, isUnique, this) }

    /**
     * Creates a unique index composed by this column only.
     *
     * @param customIndexName Name of the index.
     */
    fun <T> Column<T>.uniqueIndex(customIndexName: String? = null): Column<T> = index(customIndexName, true)

    /**
     * Creates a unique index.
     *
     * @param columns Columns that compose the index.
     */
    fun uniqueIndex(vararg columns: Column<*>): Unit = index(null, true, *columns)

    /**
     * Creates a unique index.
     *
     * @param customIndexName Name of the index.
     * @param columns Columns that compose the index.
     */
    fun uniqueIndex(customIndexName: String? = null, vararg columns: Column<*>): Unit = index(customIndexName, true, *columns)

    // Check constraints

    /**
     * Creates a check constraint in this column.
     * @param name The name to identify the constraint, optional. Must be **unique** (case-insensitive) to this table, otherwise, the constraint will
     * not be created. All names are [trimmed][String.trim], blank names are ignored and the database engine decides the default name.
     * @param op The expression against which the newly inserted values will be compared.
     */
    fun <T> Column<T>.check(name: String = "", op: SqlExpressionBuilder.(Column<T>) -> Op<Boolean>): Column<T> = apply {
        if (name.isEmpty() || table.checkConstraints.none { it.first.equals(name, true) }) {
            table.checkConstraints.add(name to SqlExpressionBuilder.op(this))
        } else {
            exposedLogger.warn("A CHECK constraint with name '$name' was ignored because there is already one with that name")
        }
    }

    /**
     * Creates a check constraint in this table.
     * @param name The name to identify the constraint, optional. Must be **unique** (case-insensitive) to this table, otherwise, the constraint will
     * not be created. All names are [trimmed][String.trim], blank names are ignored and the database engine decides the default name.
     * @param op The expression against which the newly inserted values will be compared.
     */
    fun check(name: String = "", op: SqlExpressionBuilder.() -> Op<Boolean>) {
        if (name.isEmpty() || checkConstraints.none { it.first.equals(name, true) }) {
            checkConstraints.add(name to SqlExpressionBuilder.op())
        } else {
            exposedLogger.warn("A CHECK constraint with name '$name' was ignored because there is already one with that name")
        }
    }

    // Cloning utils

    private fun <T : Any> T.clone(replaceArgs: Map<KProperty1<T, *>, Any> = emptyMap()): T = javaClass.kotlin.run {
        val consParams = primaryConstructor!!.parameters
        val mutableProperties = memberProperties.filterIsInstance<KMutableProperty1<T, Any?>>()
        val allValues = memberProperties
            .filter { it in mutableProperties || it.name in consParams.map(KParameter::name) }
            .associate { it.name to (replaceArgs[it] ?: it.get(this@clone)) }
        primaryConstructor!!.callBy(consParams.associateWith { allValues[it.name] }).also { newInstance ->
            for (prop in mutableProperties) {
                prop.set(newInstance, allValues[prop.name])
            }
        }
    }

    private fun IColumnType.cloneAsBaseType(): IColumnType = ((this as? AutoIncColumnType)?.delegate ?: this).clone()

    private fun <T> Column<T>.cloneWithAutoInc(idSeqName: String?): Column<T> = when (columnType) {
        is AutoIncColumnType -> this
        is ColumnType -> {
            val autoIncSequence = idSeqName ?: "${tableName}_${name}_seq"
            this@cloneWithAutoInc.clone<Column<T>>(mapOf(Column<T>::columnType to AutoIncColumnType(columnType, autoIncSequence)))
        }
        else -> error("Unsupported column type for auto-increment $columnType")
    }

    // DDL statements

    /** Returns the list of DDL statements that create this table. */
    val ddl: List<String> get() = createStatement()

    internal fun primaryKeyConstraint(): String? {
        return primaryKey?.let { primaryKey ->
            val tr = TransactionManager.current()
            val constraint = tr.db.identifierManager.cutIfNecessaryAndQuote(primaryKey.name)
            return primaryKey.columns.joinToString(prefix = "CONSTRAINT $constraint PRIMARY KEY (", postfix = ")", transform = tr::identity)
        }
    }

    override fun createStatement(): List<String> {
        val createSequence = autoIncColumn?.autoIncSeqName?.let { Sequence(it).createStatement() }.orEmpty()

        val addForeignKeysInAlterPart = SchemaUtils.checkCycle(this) && currentDialect !is SQLiteDialect

        val foreignKeyConstraints = columns.mapNotNull { it.foreignKey }

        val createTable = buildString {
            append("CREATE TABLE ")
            if (currentDialect.supportsIfNotExists) {
                append("IF NOT EXISTS ")
            }
            append(TransactionManager.current().identity(this@Table))
            if (columns.isNotEmpty()) {
                columns.joinTo(this, prefix = " (") { it.descriptionDdl() }

                if (isCustomPKNameDefined() || columns.none { it.isOneColumnPK() }) {
                    primaryKeyConstraint()?.let { append(", $it") }
                }

                if (!addForeignKeysInAlterPart && foreignKeyConstraints.isNotEmpty()) {
                    foreignKeyConstraints.joinTo(this, prefix = ", ", separator = ", ") { it.foreignKeyPart }
                }

                if (checkConstraints.isNotEmpty()) {
                    checkConstraints.mapIndexed { index, (name, op) ->
                        val resolvedName = name.ifBlank { "check_${tableName}_$index" }
                        CheckConstraint.from(this@Table, resolvedName, op).checkPart
                    }.joinTo(this, prefix = ", ")
                }

                append(")")
            }
        }

        val createConstraint = if (addForeignKeysInAlterPart) {
            foreignKeyConstraints.flatMap { it.createStatement() }
        } else {
            emptyList()
        }

        return createSequence + createTable + createConstraint
    }

    override fun modifyStatement(): List<String> = throw UnsupportedOperationException("Use modify on columns and indices")

    override fun dropStatement(): List<String> {
        val dropTable = buildString {
            append("DROP TABLE ")
            if (currentDialect.supportsIfNotExists) {
                append("IF EXISTS ")
            }
            append(TransactionManager.current().identity(this@Table))
            if (currentDialectIfAvailable is OracleDialect) {
                append(" CASCADE CONSTRAINTS")
            } else if (currentDialectIfAvailable is PostgreSQLDialect && SchemaUtils.checkCycle(this@Table)) {
                append(" CASCADE")
            }
        }

        val dropSequence = autoIncColumn?.autoIncSeqName?.let { Sequence(it).dropStatement() }.orEmpty()

        return listOf(dropTable) + dropSequence
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Table) return false

        if (tableName != other.tableName) return false

        return true
    }

    override fun hashCode(): Int = tableName.hashCode()
}

@Deprecated(
    "Use Sequence class instead of Seq class.",
    ReplaceWith("Sequence(name)", "org.jetbrains.exposed.sql.Sequence"),
    DeprecationLevel.ERROR
)
data class Seq(private val name: String)

/** Returns the list of tables to which the columns in this column set belong. */
fun ColumnSet.targetTables(): List<Table> = when (this) {
    is Alias<*> -> listOf(this.delegate)
    is QueryAlias -> this.query.set.source.targetTables()
    is Table -> listOf(this)
    is Join -> this.table.targetTables() + this.joinParts.flatMap { it.joinPart.targetTables() }
    else -> error("No target provided for update")
}

