@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package org.jetbrains.exposed.v1.core

import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.wrap
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.EntityIDFunctionProvider
import org.jetbrains.exposed.v1.core.dao.id.IdTable
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.core.transactions.CoreTransactionManager
import org.jetbrains.exposed.v1.core.vendors.*
import org.jetbrains.exposed.v1.exceptions.DuplicateColumnException
import java.math.BigDecimal
import java.util.*
import kotlin.internal.LowPriorityInOverloadResolution
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/** Pair of expressions used to match rows from two joined tables. */
typealias JoinCondition = Pair<Expression<*>, Expression<*>>

/** Represents a subset of fields from a given source. */
typealias Select = Slice

/**
 * Represents a set of expressions, contained in the given column set.
 */
interface FieldSet {
    /** Return the column set that contains this field set. */
    val source: ColumnSet

    /** Returns the field of this field set. */
    val fields: List<Expression<*>>

    /**
     * Returns all real fields, unrolling composite [CompositeColumn] if present
     */
    val realFields: List<Expression<*>>
        get() {
            val unrolled = ArrayList<Expression<*>>(fields.size)

            fields.forEach {
                when {
                    it is CompositeColumn<*> -> unrolled.addAll(it.getRealColumns())
                    (it as? Column<*>)?.isEntityIdentifier() == true && it.table is CompositeIdTable -> {
                        unrolled.addAll(it.table.idColumns)
                    }
                    else -> unrolled.add(it)
                }
            }

            return unrolled
        }
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
     * @param lateral Set to true to enable a lateral join, allowing the subquery on the right side
     *        to access columns from preceding tables in the FROM clause.
     * @throws IllegalStateException If join could not be prepared. See exception message for more details.
     */
    abstract fun join(
        otherTable: ColumnSet,
        joinType: JoinType,
        onColumn: Expression<*>? = null,
        otherColumn: Expression<*>? = null,
        lateral: Boolean = false,
        additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
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
}

/**
 * Creates an inner join relation with [otherTable] using [onColumn] and [otherColumn] equality
 * and/or [additionalConstraint] as the join condition.
 *
 * @throws IllegalStateException if the join cannot be performed. See the exception message for more details.
 */
fun <C1 : ColumnSet, C2 : ColumnSet> C1.innerJoin(
    otherTable: C2,
    onColumn: (C1.() -> Expression<*>)? = null,
    otherColumn: (C2.() -> Expression<*>)? = null,
    additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
): Join = join(otherTable, JoinType.INNER, onColumn?.invoke(this), otherColumn?.invoke(otherTable), false, additionalConstraint)

/**
 * Creates a left outer join relation with [otherTable] using [onColumn] and [otherColumn] equality
 * and/or [additionalConstraint] as the join condition.
 *
 * @throws IllegalStateException if the join cannot be performed. See the exception message for more details.
 */
fun <C1 : ColumnSet, C2 : ColumnSet> C1.leftJoin(
    otherTable: C2,
    onColumn: (C1.() -> Expression<*>)? = null,
    otherColumn: (C2.() -> Expression<*>)? = null,
    additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
): Join = join(otherTable, JoinType.LEFT, onColumn?.invoke(this), otherColumn?.invoke(otherTable), false, additionalConstraint)

/**
 * Creates a right outer join relation with [otherTable] using [onColumn] and [otherColumn] equality
 * and/or [additionalConstraint] as the join condition.
 *
 * @throws IllegalStateException if the join cannot be performed. See the exception message for more details.
 */
fun <C1 : ColumnSet, C2 : ColumnSet> C1.rightJoin(
    otherTable: C2,
    onColumn: (C1.() -> Expression<*>)? = null,
    otherColumn: (C2.() -> Expression<*>)? = null,
    additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
): Join = join(otherTable, JoinType.RIGHT, onColumn?.invoke(this), otherColumn?.invoke(otherTable), false, additionalConstraint)

/**
 * Creates a full outer join relation with [otherTable] using [onColumn] and [otherColumn] equality
 * and/or [additionalConstraint] as the join condition.
 *
 * @throws IllegalStateException if the join cannot be performed. See the exception message for more details.
 */
fun <C1 : ColumnSet, C2 : ColumnSet> C1.fullJoin(
    otherTable: C2,
    onColumn: (C1.() -> Expression<*>)? = null,
    otherColumn: (C2.() -> Expression<*>)? = null,
    additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
): Join = join(otherTable, JoinType.FULL, onColumn?.invoke(this), otherColumn?.invoke(otherTable), false, additionalConstraint)

/**
 * Creates a cross join relation with [otherTable] using [onColumn] and [otherColumn] equality
 * and/or [additionalConstraint] as the join condition.
 *
 * @throws IllegalStateException if the join cannot be performed. See the exception message for more details.
 */
fun <C1 : ColumnSet, C2 : ColumnSet> C1.crossJoin(
    otherTable: C2,
    onColumn: (C1.() -> Expression<*>)? = null,
    otherColumn: (C2.() -> Expression<*>)? = null,
    additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
): Join = join(otherTable, JoinType.CROSS, onColumn?.invoke(this), otherColumn?.invoke(otherTable), false, additionalConstraint)

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

    override val columns: List<Column<*>>
        get() = joinParts.flatMapTo(
            table.columns.toMutableList()
        ) { it.joinPart.columns }

    override val fields: List<Expression<*>>
        get() = joinParts.flatMapTo(table.fields.toMutableList()) {
            when (it.joinPart) {
                is QueryAlias -> it.joinPart.aliasedFields
                else -> it.joinPart.fields
            }
        }

    internal val joinParts: MutableList<JoinPart> = mutableListOf()

    constructor(
        table: ColumnSet,
        otherTable: ColumnSet,
        joinType: JoinType = JoinType.INNER,
        onColumn: Expression<*>? = null,
        otherColumn: Expression<*>? = null,
        lateral: Boolean = false,
        additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null,
    ) : this(table) {
        val newJoin = when {
            onColumn != null && otherColumn != null -> {
                join(otherTable, joinType, onColumn, otherColumn, lateral, additionalConstraint)
            }

            onColumn != null || otherColumn != null -> {
                error("Can't prepare join on $table and $otherTable when only column from a one side provided.")
            }

            additionalConstraint != null -> {
                join(otherTable, joinType, emptyList(), additionalConstraint, lateral)
            }

            else -> {
                implicitJoin(otherTable, joinType, lateral)
            }
        }
        joinParts.addAll(newJoin.joinParts)
    }

    override fun describe(s: Transaction, queryBuilder: QueryBuilder): Unit = queryBuilder {
        table.describe(s, this)
        for (p in joinParts) {
            p.describe(s, this)
        }
    }

    override fun join(
        otherTable: ColumnSet,
        joinType: JoinType,
        onColumn: Expression<*>?,
        otherColumn: Expression<*>?,
        lateral: Boolean,
        additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)?
    ): Join {
        val cond = if (onColumn != null && otherColumn != null) {
            listOf(JoinCondition(onColumn, otherColumn))
        } else {
            emptyList()
        }
        return join(otherTable, joinType, cond, additionalConstraint, lateral)
    }

    override infix fun innerJoin(otherTable: ColumnSet): Join = implicitJoin(otherTable, JoinType.INNER)

    override infix fun leftJoin(otherTable: ColumnSet): Join = implicitJoin(otherTable, JoinType.LEFT)

    override infix fun rightJoin(otherTable: ColumnSet): Join = implicitJoin(otherTable, JoinType.RIGHT)

    override infix fun fullJoin(otherTable: ColumnSet): Join = implicitJoin(otherTable, JoinType.FULL)

    override infix fun crossJoin(otherTable: ColumnSet): Join = implicitJoin(otherTable, JoinType.CROSS)

    private fun implicitJoin(
        otherTable: ColumnSet,
        joinType: JoinType,
        lateral: Boolean = false
    ): Join {
        val fkKeys = findKeys(this, otherTable) ?: findKeys(otherTable, this) ?: emptyList()
        return when {
            joinType != JoinType.CROSS && fkKeys.isEmpty() -> {
                error(
                    "Cannot join with $otherTable as there is no matching primary key/foreign key pair and constraint missing"
                )
            }

            fkKeys.any { it.second.size > 1 } -> {
                val references = fkKeys.joinToString(" & ") { "${it.first} -> ${it.second.joinToString()}" }
                error(
                    "Cannot join with $otherTable as there is multiple primary key <-> foreign key references.\n$references"
                )
            }

            else -> {
                val cond = fkKeys.filter { it.second.size == 1 }.map { it.first to it.second.single() }
                join(otherTable, joinType, cond, additionalConstraint = null, lateral = lateral)
            }
        }
    }

    @Suppress("MemberNameEqualsClassName")
    private fun join(part: JoinPart): Join = Join(table).also {
        it.joinParts.addAll(this.joinParts)
        it.joinParts.add(part)
    }

    @Suppress("MemberNameEqualsClassName")
    private fun join(
        otherTable: ColumnSet,
        joinType: JoinType,
        cond: List<JoinCondition>,
        additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)?,
        lateral: Boolean = false
    ): Join = join(JoinPart(joinType, otherTable, cond, lateral, additionalConstraint))

    private fun findKeys(a: ColumnSet, b: ColumnSet): List<Pair<Column<*>, List<Column<*>>>>? = a.columns
        .map { a_pk -> a_pk to b.columns.filter { it.referee == a_pk } }
        .filter { it.second.isNotEmpty() }
        .takeIf { it.isNotEmpty() }

    /** Return `true` if the specified [table] is already in this join, `false` otherwise. */
    fun alreadyInJoin(table: Table): Boolean = joinParts.any { it.joinPart == table }

    /** Represents a component of an existing join relation. */
    internal class JoinPart(
        /** The column set `JOIN` type. */
        val joinType: JoinType,
        /** The column set to join to other components of the relation. */
        val joinPart: ColumnSet,
        /** The [JoinCondition] expressions used to match rows from two joined tables. */
        val conditions: List<JoinCondition>,
        /** Indicates whether the LATERAL keyword should be included in the JOIN operation. */
        val lateral: Boolean = false,
        /** The conditions used to join tables, placed in the `ON` clause. */
        val additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)? = null
    ) {
        init {
            require(
                joinType == JoinType.CROSS || conditions.isNotEmpty() || additionalConstraint != null
            ) { "Missing join condition on $${this.joinPart}" }

            require(joinPart !is Table || !lateral) {
                "The LATERAL join can only be used with a subquery; it cannot be used to join table ${(joinPart as Table).tableName} directly."
            }
        }

        /** Appends the SQL representation of this join component to the specified [QueryBuilder]. */
        fun describe(transaction: Transaction, builder: QueryBuilder) = with(builder) {
            append(" $joinType JOIN ")

            if (lateral) {
                append("LATERAL ")
            }

            val isJoin = joinPart is Join
            if (isJoin) {
                append("(")
            }
            joinPart.describe(transaction, this)
            if (isJoin) {
                append(")")
            }
            if (joinType != JoinType.CROSS) {
                append(" ON ")
                appendConditions(this)
            }
        }

        /** Appends the SQL representation of the conditions in the `ON` clause to the specified [QueryBuilder]. */
        fun appendConditions(builder: QueryBuilder) = builder {
            conditions.appendTo(this, " AND ") { (pkColumn, fkColumn) -> append(pkColumn, " = ", fkColumn) }
            if (additionalConstraint != null) {
                if (conditions.isNotEmpty()) {
                    append(" AND ")
                }
                append(" (")
                append(SqlExpressionBuilder.(additionalConstraint)())
                append(")")
            }
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
@Suppress("TooManyFunctions", "LargeClass")
open class Table(name: String = "") : ColumnSet(), DdlAware {
    /** Returns the table name. */
    open val tableName: String = when {
        name.isNotEmpty() -> name
        javaClass.`package` == null -> javaClass.name.removeSuffix("Table")
        else -> javaClass.name.removePrefix("${javaClass.`package`.name}.").substringAfter('$').removeSuffix("Table")
    }

    /** Returns the schema name, or null if one does not exist for this table.
     *
     * If the table is quoted, a dot in the name is considered part of the table name and the whole string is taken to
     * be the table name as is, so there would be no schema. If it is not quoted, whatever is after the dot is
     * considered to be the table name, and whatever is before the dot is considered to be the schema.
     */
    val schemaName: String? = if (name.contains(".") && !name.isAlreadyQuoted()) {
        name.substringBeforeLast(".")
    } else {
        null
    }

    /**
     * Returns the table name without schema.
     *
     * If the table is quoted, a dot in the name is considered part of the table name and the whole string is taken to
     * be the table name as is. If it is not quoted, whatever is after the dot is considered to be the table name.
     */
    @InternalApi
    val tableNameWithoutScheme: String
        get() = if (!tableName.isAlreadyQuoted()) tableName.substringAfterLast(".") else tableName

    /**
     * Returns the table name without schema, with all quotes removed.
     *
     * Used for two purposes:
     * 1. Forming primary and foreign key names
     * 2. Comparing table names from database metadata (except MySQL and MariaDB)
     * @see org.jetbrains.exposed.v1.sql.vendors.VendorDialect.metadataMatchesTable
     */
    @InternalApi
    val tableNameWithoutSchemeSanitized: String
        get() = tableNameWithoutScheme.unquoted()

    /**
     * Returns the full table name with all quotes removed. If the table name includes a dot-prefixed schema name,
     * the full name will be returned with '_' replacing the dot characters.
     */
    private val tableNameWithSchemaSanitized: String
        get() = tableName.unquoted().replace('.', '_')

    private fun String.unquoted(): String = replace("\"", "").replace("'", "").replace("`", "")

    private val _columns = mutableListOf<Column<*>>()

    /** Returns all the columns defined on the table. */
    override val columns: List<Column<*>> get() = _columns

    /** Returns the first auto-increment column on the table. */
    val autoIncColumn: Column<*>? get() = columns.firstOrNull { it.columnType.isAutoInc }

    private val _indices = mutableListOf<Index>()

    /** Returns all indices declared on the table. */
    val indices: List<Index> get() = _indices

    private val _foreignKeys = mutableListOf<ForeignKeyConstraint>()

    /** Returns all foreign key constraints declared on the table. */
    val foreignKeys: List<ForeignKeyConstraint> get() = columns.mapNotNull { it.foreignKey } + _foreignKeys

    /**
     * Returns all sequences declared on the table, along with any auto-generated sequences that are not explicitly
     * declared by the user but associated with the table.
     */
    val sequences: List<Sequence>
        get() = columns.filter { it.columnType.isAutoInc }.mapNotNull { column ->
            column.autoIncColumnType?.sequence
                ?: column.takeIf { currentDialect is PostgreSQLDialect }?.let {
                    val fallbackSequenceName = fallbackSequenceName(tableName = tableName, columnName = it.name)
                    Sequence(
                        fallbackSequenceName,
                        startWith = 1,
                        minValue = 1,
                        maxValue = currentDialect.sequenceMaxValue
                    )
                }
        }

    private val checkConstraints = mutableListOf<Pair<String, Op<Boolean>>>()

    private val generatedUnsignedCheckPrefix
        get() = "chk_${tableNameWithSchemaSanitized}_unsigned_"

    private val generatedSignedCheckPrefix
        get() = "chk_${tableNameWithSchemaSanitized}_signed_"

    /** Returns the list of CHECK constraints in this table. */
    fun checkConstraints(): List<CheckConstraint> {
        val filteredChecks = checkConstraints.filterNot { (name, _) ->
            when (val dialect = currentDialect) {
                is MysqlDialect -> name.startsWith(generatedUnsignedCheckPrefix) || name.startsWith(generatedSignedCheckPrefix)
                is SQLServerDialect -> name.startsWith("${generatedUnsignedCheckPrefix}byte") ||
                    name.startsWith("${generatedSignedCheckPrefix}short")
                is PostgreSQLDialect -> name.startsWith("${generatedSignedCheckPrefix}short")
                is H2Dialect -> when (dialect.h2Mode) {
                    H2Dialect.H2CompatibilityMode.PostgreSQL -> name.startsWith("${generatedSignedCheckPrefix}short")
                    else -> name.startsWith(generatedSignedCheckPrefix)
                }
                else -> false
            }
        }.toMutableList().apply {
            val isNotSQLiteOrOracle = currentDialect !is SQLiteDialect && currentDialect !is OracleDialect
            val isNotOracle = currentDialect !is OracleDialect

            if (isNotSQLiteOrOracle) removeAll { (name, _) -> name.startsWith("${generatedSignedCheckPrefix}integer") }
            if (isNotOracle) removeAll { (name, _) -> name.startsWith("${generatedSignedCheckPrefix}long") }
        }
        return filteredChecks.mapIndexed { index, (name, op) ->
            val resolvedName = name.ifBlank { "check_${tableNameWithSchemaSanitized}_$index" }
            CheckConstraint.from(this@Table, resolvedName, op)
        }
    }

    /**
     * Returns the table name in proper case.
     * Should be called within transaction or default [tableName] will be returned.
     */
    @OptIn(InternalApi::class)
    fun nameInDatabaseCase(): String = tableName.inProperCase()

    /**
     * Returns the table name, without schema and in proper case, with wrapping single- and double-quotation characters removed.
     *
     * **Note** If used with MySQL or MariaDB, the table name is returned unchanged, since these databases use a
     * backtick character as the identifier quotation.
     */
    fun nameInDatabaseCaseUnquoted(): String = if (currentDialect is MysqlDialect) {
        @OptIn(InternalApi::class)
        tableNameWithoutScheme.inProperCase()
    } else {
        @OptIn(InternalApi::class)
        tableNameWithoutScheme.inProperCase().trim('\"', '\'')
    }

    override fun describe(s: Transaction, queryBuilder: QueryBuilder): Unit = queryBuilder {
        append(
            s.identity(this@Table)
        )
    }

    // Join operations

    override fun join(
        otherTable: ColumnSet,
        joinType: JoinType,
        onColumn: Expression<*>?,
        otherColumn: Expression<*>?,
        lateral: Boolean,
        additionalConstraint: (SqlExpressionBuilder.() -> Op<Boolean>)?
    ): Join = Join(this, otherTable, joinType, onColumn, otherColumn, lateral, additionalConstraint)

    override infix fun innerJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.INNER)

    override infix fun leftJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.LEFT)

    override infix fun rightJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.RIGHT)

    override infix fun fullJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.FULL)

    override infix fun crossJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.CROSS)

    // Column registration

    /** Adds a column of the specified [type] and with the specified [name] to the table. */
    fun <T> registerColumn(name: String, type: IColumnType<T & Any>): Column<T> = Column<T>(
        this,
        name,
        type
    ).also { _columns.addColumn(it) }

    /** Adds all wrapped column components of a [CompositeColumn] to the table. */
    fun <R, T : CompositeColumn<R>> registerCompositeColumn(column: T): T = column.apply {
        getRealColumns().forEach {
            _columns.addColumn(
                it
            )
        }
    }

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

    internal fun isCustomPKNameDefined(): Boolean = primaryKey?.let {
        @OptIn(InternalApi::class)
        it.name != "pk_$tableNameWithoutSchemeSanitized"
    } == true

    /**
     * Represents a primary key composed by the specified [columns], and with the specified [name].
     * If no name is specified, the table name with the "pk_" prefix will be used instead.
     *
     * @sample org.jetbrains.exposed.v1.tests.demo.sql.Users
     */
    inner class PrimaryKey(
        /** Returns the columns that compose the primary key. */
        val columns: Array<Column<*>>,
        name: String? = null
    ) {
        /** Returns the name of the primary key. */
        val name: String by lazy {
            @OptIn(InternalApi::class)
            name ?: "pk_$tableNameWithoutSchemeSanitized"
        }

        constructor(firstColumn: Column<*>, vararg columns: Column<*>, name: String? = null) :
            this(arrayOf(firstColumn) + columns.asList(), name)

        init {
            columns.sortWith(compareBy { !it.columnType.isAutoInc })
        }
    }

    /**
     * Returns the primary key of the table if present, `null` otherwise.
     *
     * The primary key can be defined explicitly by overriding the property directly or by using one of the predefined
     * table types like `IntIdTable`, `LongIdTable`, or `UUIDIdTable`.
     */
    open val primaryKey: PrimaryKey? = null

    // EntityID columns

    /** Converts the @receiver column to an [EntityID] column. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> Column<T>.entityId(): Column<EntityID<T>> {
        val newColumn = Column<EntityID<T>>(table, name, EntityIDColumnType(this)).also {
            it.defaultValueFun = defaultValueFun?.let { { EntityIDFunctionProvider.createEntityID(it(), table as IdTable<T>) } }
            it.dbDefaultValue = dbDefaultValue?.let { default -> default as Expression<EntityID<T>> }
            it.extraDefinitions = extraDefinitions
        }
        (table as IdTable<T>).addIdColumnInternal(newColumn)
        return replaceColumn(this, newColumn)
    }

    /** Creates an [EntityID] column, with the specified [name], for storing the same objects as the specified [originalColumn]. */
    fun <ID : Any> entityId(name: String, originalColumn: Column<ID>): Column<EntityID<ID>> {
        val columnTypeCopy = originalColumn.columnType.cloneAsBaseType()
        val answer = Column<EntityID<ID>>(
            this,
            name,
            EntityIDColumnType(Column<ID>(originalColumn.table, name, columnTypeCopy))
        )
        _columns.addColumn(answer)
        return answer
    }

    /** Creates an [EntityID] column, with the specified [name], for storing the identifier of the specified [table]. */
    @Suppress("UNCHECKED_CAST")
    fun <ID : Any> entityId(name: String, table: IdTable<ID>): Column<EntityID<ID>> {
        val originalColumn = (table.id.columnType as EntityIDColumnType<*>).idColumn as Column<ID>
        return entityId(name, originalColumn)
    }

    /**
     * Returns a boolean operator comparing each of an IdTable's `idColumns` to its corresponding
     * value in [toCompare], using the specified SQL [booleanOperator].
     *
     * @throws IllegalStateException If this is not an [IdTable], or if [toCompare] is either not
     * a matching id type or it does not contain a key for each component column.
     */
    internal open fun mapIdComparison(
        toCompare: Any?,
        booleanOperator: (Column<*>, Expression<*>) -> Op<Boolean>
    ): Op<Boolean> {
        require(this is IdTable<*>) { "idColumns for mapping are only available from IdTable instances" }
        val singleId = idColumns.single()
        return booleanOperator(singleId, singleId.wrap(toCompare))
    }

    /** Returns a boolean operator with each of an IdTable's `idColumns` using the specified SQL [booleanOperator]. */
    internal open fun mapIdOperator(
        booleanOperator: (Column<*>) -> Op<Boolean>
    ): Op<Boolean> {
        require(this is IdTable<*>) { "idColumns for mapping are only available from IdTable instances" }
        return booleanOperator(idColumns.single())
    }

    // Numeric columns

    /** Creates a numeric column, with the specified [name], for storing 1-byte integers.
     * An optional [checkConstraintName] can be passed to allow customizing the check constraint name when needed.
     */
    fun byte(name: String, checkConstraintName: String? = null): Column<Byte> = registerColumn(name, ByteColumnType()).apply {
        check(checkConstraintName ?: "${generatedSignedCheckPrefix}byte_${this.unquotedName()}") { it.between(Byte.MIN_VALUE, Byte.MAX_VALUE) }
    }

    /** Creates a numeric column, with the specified [name], for storing 1-byte unsigned integers.
     * An optional [checkConstraintName] can be passed to allow customizing the check constraint name when needed.
     *
     * **Note:** If the database being used is not MySQL, MariaDB, or SQL Server, this column will use the
     * database's 2-byte integer type with a check constraint that ensures storage of only values
     * between 0 and [UByte.MAX_VALUE] inclusive.
     */
    fun ubyte(name: String, checkConstraintName: String? = null): Column<UByte> = registerColumn(name, UByteColumnType()).apply {
        check(checkConstraintName ?: "${generatedUnsignedCheckPrefix}byte_${this.unquotedName()}") { it.between(0u, UByte.MAX_VALUE) }
    }

    /** Creates a numeric column, with the specified [name], for storing 2-byte integers.
     * An optional [checkConstraintName] can be passed to allow customizing the check constraint name when needed.
     */
    fun short(name: String, checkConstraintName: String? = null): Column<Short> = registerColumn(name, ShortColumnType()).apply {
        check(checkConstraintName ?: "${generatedSignedCheckPrefix}short_${this.unquotedName()}") { it.between(Short.MIN_VALUE, Short.MAX_VALUE) }
    }

    /** Creates a numeric column, with the specified [name], for storing 2-byte unsigned integers.
     * An optional [checkConstraintName] can be passed to allow customizing the check constraint name when needed.
     *
     * **Note:** If the database being used is not MySQL or MariaDB, this column will use the database's 4-byte
     * integer type with a check constraint that ensures storage of only values between 0 and [UShort.MAX_VALUE] inclusive.
     */
    fun ushort(name: String, checkConstraintName: String? = null): Column<UShort> = registerColumn(name, UShortColumnType()).apply {
        check(checkConstraintName ?: "${generatedUnsignedCheckPrefix}short_${this.unquotedName()}") { it.between(0u, UShort.MAX_VALUE) }
    }

    /** Creates a numeric column, with the specified [name], for storing 4-byte integers.
     * An optional [checkConstraintName] can be passed to allow customizing the check constraint name when needed.
     */
    fun integer(name: String, checkConstraintName: String? = null): Column<Int> = registerColumn(name, IntegerColumnType()).apply {
        check(checkConstraintName ?: "${generatedSignedCheckPrefix}integer_${this.unquotedName()}") { it.between(Int.MIN_VALUE, Int.MAX_VALUE) }
    }

    /** Creates a numeric column, with the specified [name], for storing 4-byte unsigned integers.
     * An optional [checkConstraintName] can be passed to allow customizing the check constraint name when needed.
     *
     * **Note:** If the database being used is not MySQL or MariaDB, this column will use the database's
     * 8-byte integer type with a check constraint that ensures storage of only values
     * between 0 and [UInt.MAX_VALUE] inclusive.
     */
    fun uinteger(name: String, checkConstraintName: String? = null): Column<UInt> = registerColumn(name, UIntegerColumnType()).apply {
        check(checkConstraintName ?: "${generatedUnsignedCheckPrefix}integer_${this.unquotedName()}") { it.between(0u, UInt.MAX_VALUE) }
    }

    /** Creates a numeric column, with the specified [name], for storing 8-byte integers.
     * An optional [checkConstraintName] can be passed to allow customizing the check constraint name when needed.
     */
    fun long(name: String, checkConstraintName: String? = null): Column<Long> = registerColumn(name, LongColumnType()).apply {
        check(checkConstraintName ?: "${generatedSignedCheckPrefix}long_${this.unquotedName()}") { it.between(Long.MIN_VALUE, Long.MAX_VALUE) }
    }

    /** Creates a numeric column, with the specified [name], for storing 8-byte unsigned integers.
     *
     * **Note:** For PostgreSQL, the maximum value this column will store is [Long.MAX_VALUE].
     */
    fun ulong(name: String): Column<ULong> = registerColumn(name, ULongColumnType())

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
    fun decimal(name: String, precision: Int, scale: Int): Column<BigDecimal> = registerColumn(
        name,
        DecimalColumnType(precision, scale)
    )

    // Character columns

    /** Creates a character column, with the specified [name], for storing single characters. */
    fun char(name: String): Column<Char> = registerColumn(name, CharacterColumnType())

    /**
     * Creates a character column, with the specified [name], for storing strings with the specified [length] using the specified text [collate] type.
     * If no collate type is specified then the database default is used.
     */
    fun char(name: String, length: Int, collate: String? = null): Column<String> = registerColumn(
        name,
        CharColumnType(length, collate)
    )

    /**
     * Creates a character column, with the specified [name], for storing strings with the specified maximum [length] using the specified text [collate] type.
     * If no collate type is specified then the database default is used.
     */
    fun varchar(name: String, length: Int, collate: String? = null): Column<String> = registerColumn(
        name,
        VarCharColumnType(length, collate)
    )

    /**
     * Creates a character column, with the specified [name], for storing strings of arbitrary length using the specified [collate] type.
     * If no collated type is specified, then the database default is used.
     *
     * Some database drivers do not load text content immediately (for performance and memory reasons),
     * which means that you can obtain column value only within the open transaction.
     * If you desire to make content available outside the transaction use [eagerLoading] param.
     */
    fun text(name: String, collate: String? = null, eagerLoading: Boolean = false): Column<String> =
        registerColumn(name, TextColumnType(collate, eagerLoading))

    /**
     * Creates a character column, with the specified [name], for storing strings of _medium_ length using the specified [collate] type.
     * If no collated type is specified, then the database default is used.
     *
     * Some database drivers do not load text content immediately (for performance and memory reasons),
     * which means that you can obtain column value only within the open transaction.
     * If you desire to make content available outside the transaction use [eagerLoading] param.
     */
    fun mediumText(name: String, collate: String? = null, eagerLoading: Boolean = false): Column<String> =
        registerColumn(name, MediumTextColumnType(collate, eagerLoading))

    /**
     * Creates a character column, with the specified [name], for storing strings of _large_ length using the specified [collate] type.
     * If no collated type is specified, then the database default is used.
     *
     * Some database drivers do not load text content immediately (for performance and memory reasons),
     * which means that you can obtain column value only within the open transaction.
     * If you desire to make content available outside the transaction use [eagerLoading] param.
     */
    fun largeText(name: String, collate: String? = null, eagerLoading: Boolean = false): Column<String> =
        registerColumn(name, LargeTextColumnType(collate, eagerLoading))

    // Binary columns

    /**
     * Creates a binary column, with the specified [name], for storing byte arrays of arbitrary size.
     *
     * **Note:** This function is only supported by SQLite, PostgreSQL, and H2 dialects.
     * For the rest, please specify a length.
     * For H2 dialects, the maximum size is 1,000,000,000 bytes.
     *
     * @sample org.jetbrains.exposed.v1.tests.shared.DDLTests.testBinaryWithoutLength
     */
    fun binary(name: String): Column<ByteArray> = registerColumn(name, BasicBinaryColumnType())

    /**
     * Creates a binary column, with the specified [name], for storing byte arrays with the specified maximum [length].
     *
     * **Note:** The length of the binary column is not required in PostgreSQL and will be ignored.
     *
     * @sample org.jetbrains.exposed.v1.tests.shared.DDLTests.testBinary
     */
    fun binary(name: String, length: Int): Column<ByteArray> = registerColumn(name, BinaryColumnType(length))

    /**
     * Creates a binary column, with the specified [name], for storing BLOBs.
     * If [useObjectIdentifier] is `true`, then the column will use the `OID` type on PostgreSQL
     * for storing large binary objects. The parameter must not be `true` for other databases.
     *
     * @sample org.jetbrains.exposed.v1.tests.shared.types.BlobColumnTypeTests.testBlob
     */
    fun blob(name: String, useObjectIdentifier: Boolean = false): Column<ExposedBlob> =
        registerColumn(name, BlobColumnType(useObjectIdentifier))

    /** Creates a binary column, with the specified [name], for storing UUIDs. */
    fun uuid(name: String): Column<UUID> = registerColumn(name, UUIDColumnType())

    // Boolean columns

    /** Creates a column, with the specified [name], for storing boolean values. */
    fun bool(name: String): Column<Boolean> = registerColumn(name, BooleanColumnType())

    // Enumeration columns

    /** Creates an enumeration column, with the specified [name], for storing enums of type [klass] by their ordinal. */
    fun <T : Enum<T>> enumeration(name: String, klass: KClass<T>): Column<T> = registerColumn(
        name,
        EnumerationColumnType(klass)
    )

    /** Creates an enumeration column, with the specified [name], for storing enums of type [T] by their ordinal. */
    inline fun <reified T : Enum<T>> enumeration(name: String) = enumeration(name, T::class)

    /**
     * Creates an enumeration column, with the specified [name], for storing enums of type [klass] by their name.
     * With the specified maximum [length] for each name value.
     */
    fun <T : Enum<T>> enumerationByName(name: String, length: Int, klass: KClass<T>): Column<T> =
        registerColumn(name, EnumerationNameColumnType(klass, length))

    /**
     * Creates an enumeration column, with the specified [name], for storing enums of type [T] by their name.
     * With the specified maximum [length] for each name value.
     */
    inline fun <reified T : Enum<T>> enumerationByName(name: String, length: Int) =
        enumerationByName(name, length, T::class)

    /**
     * Creates an enumeration column, with the custom SQL type [sql], for storing enums of type [T] using this database-specific type.
     *
     * See [Wiki](https://github.com/JetBrains/Exposed/wiki/DataTypes#how-to-use-database-enum-types) for more details.
     *
     * @param name Name of the column
     * @param sql SQL definition for the column
     * @param fromDb Function that converts a value received from a database to an enumeration instance [T]
     * @param toDb Function that converts an enumeration instance [T] to a value that will be stored to a database
     */
    fun <T : Enum<T>> customEnumeration(
        name: String,
        sql: String? = null,
        fromDb: (Any) -> T,
        toDb: (T) -> Any
    ): Column<T> = registerColumn(name, CustomEnumerationColumnType(name, sql, fromDb, toDb))

    // Array columns

    /**
     * Creates an array column, with the specified [name], for storing elements of a `List` using a base [columnType].
     *
     * **Note** This column type is only supported by H2 and PostgreSQL dialects.
     *
     * @param name Name of the column.
     * @param columnType Base column type for the individual elements.
     * @param maximumCardinality The maximum amount of allowed elements. **Note** Providing an array size limit
     * when using the PostgreSQL dialect is allowed, but this value will be ignored by the database.
     */
    fun <E> array(name: String, columnType: ColumnType<E & Any>, maximumCardinality: Int? = null): Column<List<E>> =
        array<E, List<E>>(name, columnType, dimensions = 1, maximumCardinality = maximumCardinality?.let { listOf(it) })

    /**
     * Creates an array column, with the specified [name], for storing elements of a `List`.
     *
     * **Note** This column type is only supported by H2 and PostgreSQL dialects.
     *
     * **Note** The base column type associated with storing elements of type [E] will be resolved according to
     * the internal mapping in [resolveColumnType]. To avoid this type reflection, or if a mapping does not exist
     * for the elements being stored, please provide an explicit column type to the [array] overload. If the elements
     * to be stored are nullable, an explicit column type will also need to be provided.
     *
     * @param name Name of the column.
     * @param maximumCardinality The maximum amount of allowed elements. **Note** Providing an array size limit
     * when using the PostgreSQL dialect is allowed, but this value will be ignored by the database.
     * @throws IllegalStateException If no column type mapping is found.
     */
    inline fun <reified E : Any> array(name: String, maximumCardinality: Int? = null): Column<List<E>> =
        array<E, List<E>>(name, maximumCardinality?.let { listOf(it) }, dimensions = 1)

    /**
     * Creates a multi-dimensional array column, with the specified [name], for storing elements of a nested `List`.
     * The number of dimensions is specified by the [dimensions] parameter.
     *
     * **Note:** This column type is only supported by PostgreSQL dialect.
     *
     * @param name Name of the column.
     * @param maximumCardinality The maximum cardinality (number of allowed elements) for each dimension in the array.
     * @param dimensions The number of dimensions of the array.
     *
     * **Note:** Providing an array size limit when using the PostgreSQL dialect is allowed, but this value will be ignored by the database.
     *
     * @return A column instance that represents a multi-dimensional list of elements of type [T].
     * @throws IllegalStateException If no column type mapping is found.
     */
    inline fun <reified T : Any, R : List<Any>> Table.array(name: String, maximumCardinality: List<Int>? = null, dimensions: Int): Column<R> {
        @OptIn(InternalApi::class)
        return array(name, resolveColumnType(T::class), maximumCardinality, dimensions)
    }

    /**
     * Creates a multi-dimensional array column, with the specified [name], for storing elements of a nested `List`.
     * The number of dimensions is specified by the [dimensions] parameter.
     *
     * **Note:** This column type is only supported by PostgreSQL dialect.
     *
     * @param name Name of the column.
     * @param maximumCardinality The maximum cardinality (number of allowed elements) for each dimension in the array.
     * @param dimensions The number of dimensions of the array.
     *
     * **Note:** Providing an array size limit when using the PostgreSQL dialect is allowed, but this value will be ignored by the database.
     *
     * @return A column instance that represents a multi-dimensional list of elements of type [E].
     * @throws IllegalStateException If no column type mapping is found.
     */
    fun <E, R : List<Any?>> Table.array(name: String, columnType: ColumnType<E & Any>, maximumCardinality: List<Int>? = null, dimensions: Int): Column<R> =
        registerColumn(name, ArrayColumnType(columnType, maximumCardinality, dimensions))

    // Auto-generated values

    /**
     * Make @receiver column an auto-increment column to generate its values in a database.
     * **Note:** Only integer and long columns are supported (signed and unsigned types).
     * Some databases, like PostgreSQL, support auto-increment via sequences.
     * In this case a name should be provided using the [idSeqName] param and Exposed will create a sequence.
     * If a sequence already exists in the database just use its name in [idSeqName].
     *
     * @param idSeqName an optional parameter to provide a sequence name
     */
    fun <N : Any> Column<N>.autoIncrement(idSeqName: String? = null): Column<N> =
        cloneWithAutoInc(idSeqName).also { replaceColumn(this, it) }

    /**
     * Make @receiver column an auto-increment column to generate its values in a database.
     * **Note:** Only integer and long columns are supported (signed and unsigned types).
     * Some databases, like PostgreSQL, support auto-increment via sequences.
     * In this case, a sequence should be provided using the [sequence] param.
     *
     * @param sequence a parameter to provide a sequence
     */
    fun <N : Any> Column<N>.autoIncrement(sequence: Sequence): Column<N> =
        cloneWithAutoInc(sequence).also { replaceColumn(this, it) }

    @Deprecated(
        message = "This function will be removed in future releases.",
        replaceWith = ReplaceWith("autoIncrement(idSeqName)"),
        level = DeprecationLevel.WARNING
    )
    fun <N : Any> Column<EntityID<N>>.autoinc(idSeqName: String? = null): Column<EntityID<N>> =
        cloneWithAutoInc(idSeqName).also { replaceColumn(this, it) }

    /** Sets the default value for this column in the database side. */
    fun <T> Column<T>.default(defaultValue: T): Column<T> = apply {
        dbDefaultValue = with(SqlExpressionBuilder) { asLiteral(defaultValue) }
        defaultValueFun = { defaultValue }
    }

    /** Sets the default value for this column in the database side. */
    fun <T> CompositeColumn<T>.default(defaultValue: T): CompositeColumn<T> = apply {
        with(this@Table) {
            this@default.getRealColumnsWithValues(defaultValue).forEach {
                (it.key as Column<Any>).default(it.value as Any)
            }
        }
    }

    /** Sets the default value for this column in the database side. */
    fun <T> Column<T>.defaultExpression(defaultValue: Expression<T>): Column<T> = apply {
        dbDefaultValue = defaultValue
        defaultValueFun = null
    }

    /** Sets the default value for this column in the client side. */
    fun <T> Column<T>.clientDefault(defaultValue: () -> T): Column<T> = apply {
        dbDefaultValue = null
        defaultValueFun = defaultValue
    }

    /**
     * Marks a column as `databaseGenerated` if the default value of the column is not known at the time of table creation
     * and/or if it depends on other columns. It makes it possible to omit setting it when inserting a new record,
     * without getting an error.
     * The value for the column can be set by creating a TRIGGER or with a DEFAULT clause or
     * by using GENERATED ALWAYS AS via [Column.withDefinition], for example.
     */
    fun <T> Column<T>.databaseGenerated(): Column<T> = apply {
        isDatabaseGenerated = true
    }

    /** UUID column will auto generate its value on a client side just before an insert. */
    fun Column<UUID>.autoGenerate(): Column<UUID> = clientDefault { UUID.randomUUID() }

    // Column references

    /**
     * Creates a reference from this @receiver column to a [ref] column.
     *
     * This is a short infix version of `references()` with default `onDelete` and `onUpdate` behavior.
     *
     * @receiver A column from the current table where reference values will be stored.
     * @param ref A column from another table which will be used as a "parent".
     * @sample org.jetbrains.exposed.v1.tests.shared.dml.JoinTests.testJoin04
     */
    infix fun <T : Any, S : T, C : Column<S>> C.references(ref: Column<T>): C = references(
        ref,
        null,
        null,
        null
    )

    /**
     * Creates a reference from this @receiver column to a [ref] column with [onDelete], [onUpdate], and [fkName] options.
     * [onDelete] and [onUpdate] options describe the behavior for how links between tables will be checked when deleting
     * or changing corresponding columns' values.
     * Such a relationship will be represented as a FOREIGN KEY constraint on table creation.
     *
     * @receiver A column from the current table where reference values will be stored.
     * @param ref A column from another table which will be used as a "parent".
     * @param onDelete Optional [ReferenceOption] for cases when a linked row from a parent table will be deleted.
     * @param onUpdate Optional [ReferenceOption] for cases when a value in a referenced column will be changed.
     * @param fkName Optional foreign key constraint name.
     * @sample org.jetbrains.exposed.v1.tests.sqlite.ForeignKeyConstraintTests.testUpdateAndDeleteRulesReadCorrectlyWhenSpecifiedInChildTable
     */
    fun <T : Any, S : T, C : Column<S>> C.references(
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
     * Creates a reference from this @receiver column to a [ref] column with [onDelete], [onUpdate], and [fkName] options.
     * [onDelete] and [onUpdate] options describe the behavior for how links between tables will be checked when deleting
     * or changing corresponding columns' values.
     * Such a relationship will be represented as a FOREIGN KEY constraint on table creation.
     *
     * @receiver A column from the current table where reference values will be stored.
     * @param ref A column from another table which will be used as a "parent".
     * @param onDelete Optional [ReferenceOption] for cases when a linked row from a parent table will be deleted.
     * @param onUpdate Optional [ReferenceOption] for cases when a value in a referenced column will be changed.
     * @param fkName Optional foreign key constraint name.
     * @sample org.jetbrains.exposed.v1.tests.shared.ddl.CreateMissingTablesAndColumnsTests.ExplicitTable
     */
    @JvmName("referencesById")
    fun <T : Any, S : T, C : Column<S>> C.references(
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
     * Creates a column with the specified [name] with a reference to the [refColumn] column and with [onDelete],
     * [onUpdate], and [fkName] options.
     * [onDelete] and [onUpdate] options describe the behavior for how links between tables will be checked when deleting
     * or changing corresponding columns' values.
     * Such a relationship will be represented as a FOREIGN KEY constraint on table creation.
     *
     * @param name Name of the column.
     * @param refColumn A column from another table which will be used as a "parent".
     * @param onDelete Optional [ReferenceOption] for cases when a linked row from a parent table will be deleted.
     * @param onUpdate Optional [ReferenceOption] for cases when a value in a referenced column will be changed.
     * @param fkName Optional foreign key constraint name.
     * @sample org.jetbrains.exposed.v1.tests.shared.entities.EntityTests.Orders
     */
    fun <T : Any> reference(
        name: String,
        refColumn: Column<T>,
        onDelete: ReferenceOption? = null,
        onUpdate: ReferenceOption? = null,
        fkName: String? = null
    ): Column<T> {
        val column = Column(
            this,
            name,
            refColumn.columnType.cloneAsBaseType()
        ).references(refColumn, onDelete, onUpdate, fkName)
        _columns.addColumn(column)
        return column
    }

    /**
     * Creates a column with the specified [name] with a reference to the [refColumn] column and with [onDelete],
     * [onUpdate], and [fkName] options.
     * [onDelete] and [onUpdate] options describe the behavior for how links between tables will be checked when deleting
     * or changing corresponding columns' values.
     * Such a relationship will be represented as a FOREIGN KEY constraint on table creation.
     *
     * @param name Name of the column.
     * @param refColumn A column from another table which will be used as a "parent".
     * @param onDelete Optional [ReferenceOption] for cases when a linked row from a parent table will be deleted.
     * @param onUpdate Optional [ReferenceOption] for cases when a value in a referenced column will be changed.
     * @param fkName Optional foreign key constraint name.
     * @sample org.jetbrains.exposed.v1.tests.shared.entities.EntityTests.Schools
     */
    @Suppress("UNCHECKED_CAST")
    @JvmName("referenceByIdColumn")
    fun <T : Any, E : EntityID<T>> reference(
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
     * Creates a column with the specified [name] with a reference to the `id` column in [foreign] table and with
     * [onDelete], [onUpdate], and [fkName] options.
     * [onDelete] and [onUpdate] options describe the behavior for how links between tables will be checked when deleting
     * or changing corresponding columns' values.
     * Such a relationship will be represented as a FOREIGN KEY constraint on table creation.
     *
     * @param name Name of the column.
     * @param foreign A table with an `id` column which will be used as a "parent".
     * @param onDelete Optional [ReferenceOption] for cases when a linked row from a parent table will be deleted.
     * @param onUpdate Optional [ReferenceOption] for cases when a value in a referenced column will be changed.
     * @param fkName Optional foreign key constraint name.
     * @sample org.jetbrains.exposed.v1.tests.shared.entities.EntityTests.Schools
     */
    fun <T : Any> reference(
        name: String,
        foreign: IdTable<T>,
        onDelete: ReferenceOption? = null,
        onUpdate: ReferenceOption? = null,
        fkName: String? = null
    ): Column<EntityID<T>> {
        require(foreign !is CompositeIdTable || foreign.idColumns.size == 1) {
            "Use foreignKey() to create a foreign key constraint involving multiple key columns."
        }
        return entityId(name, foreign).references(foreign.id, onDelete, onUpdate, fkName)
    }

    /**
     * Creates a column with the specified [name] with an optional reference to the [refColumn] column with [onDelete], [onUpdate], and [fkName] options.
     * [onDelete] and [onUpdate] options describe the behavior for how links between tables will be checked when deleting
     * or changing corresponding columns' values.
     * Such a relationship will be represented as a FOREIGN KEY constraint on table creation.
     *
     * @param name Name of the column.
     * @param refColumn A column from another table which will be used as a "parent".
     * @param onDelete Optional [ReferenceOption] for cases when a linked row from a parent table will be deleted.
     * @param onUpdate Optional [ReferenceOption] for cases when a value in a referenced column will be changed.
     * @param fkName Optional foreign key constraint name.
     * @sample org.jetbrains.exposed.v1.tests.shared.entities.EntityTests.Posts
     */
    fun <T : Any> optReference(
        name: String,
        refColumn: Column<T>,
        onDelete: ReferenceOption? = null,
        onUpdate: ReferenceOption? = null,
        fkName: String? = null
    ): Column<T?> = reference(name, refColumn, onDelete, onUpdate, fkName).nullable()

    /**
     * Creates a column with the specified [name] with an optional reference to the [refColumn] column with [onDelete], [onUpdate], and [fkName] options.
     * [onDelete] and [onUpdate] options describe the behavior for how links between tables will be checked when deleting
     * or changing corresponding columns' values.
     * Such a relationship will be represented as a FOREIGN KEY constraint on table creation.
     *
     * @param name Name of the column.
     * @param refColumn A column from another table which will be used as a "parent".
     * @param onDelete Optional [ReferenceOption] for cases when a linked row from a parent table will be deleted.
     * @param onUpdate Optional [ReferenceOption] for cases when a value in a referenced column will be changed.
     * @sample org.jetbrains.exposed.v1.tests.shared.entities.EntityTests.Posts
     */
    @JvmName("optReferenceByIdColumn")
    fun <T : Any, E : EntityID<T>> optReference(
        name: String,
        refColumn: Column<E>,
        onDelete: ReferenceOption? = null,
        onUpdate: ReferenceOption? = null,
        fkName: String? = null
    ): Column<E?> = reference(name, refColumn, onDelete, onUpdate, fkName).nullable()

    /**
     * Creates a column with the specified [name] with an optional reference to the `id` column in [foreign] table with [onDelete], [onUpdate], and [fkName] options.
     * [onDelete] and [onUpdate] options describe the behavior for how links between tables will be checked when deleting
     * or changing corresponding columns' values.
     * Such a relationship will be represented as a FOREIGN KEY constraint on table creation.
     *
     * @param name Name of the column.
     * @param foreign A table with an `id` column which will be used as a "parent".
     * @param onDelete Optional [ReferenceOption] for cases when a linked row from a parent table will be deleted.
     * @param onUpdate Optional [ReferenceOption] for cases when a value in a referenced column will be changed.
     * @param fkName Optional foreign key constraint name.
     * @sample org.jetbrains.exposed.v1.tests.shared.entities.EntityTests.Schools
     */
    fun <T : Any> optReference(
        name: String,
        foreign: IdTable<T>,
        onDelete: ReferenceOption? = null,
        onUpdate: ReferenceOption? = null,
        fkName: String? = null
    ): Column<EntityID<T>?> = reference(name, foreign, onDelete, onUpdate, fkName).nullable()

    // Miscellaneous

    /** Marks this column as nullable. */
    fun <T : Any> Column<T>.nullable(): Column<T?> {
        val newColumn = Column<T?>(table, name, columnType)
        newColumn.foreignKey = foreignKey
        newColumn.defaultValueFun = defaultValueFun
        @Suppress("UNCHECKED_CAST")
        newColumn.dbDefaultValue = dbDefaultValue as Expression<T?>?
        newColumn.isDatabaseGenerated = isDatabaseGenerated
        newColumn.columnType.nullable = true
        newColumn.extraDefinitions = extraDefinitions
        return replaceColumn(this, newColumn)
    }

    /** Marks this [CompositeColumn] as nullable. */
    @Suppress("UNCHECKED_CAST")
    @LowPriorityInOverloadResolution
    fun <T : Any, C : CompositeColumn<T>> C.nullable(): CompositeColumn<T?> = apply {
        nullable = true
        getRealColumns().filter { !it.columnType.nullable }.forEach { (it as Column<Any>).nullable() }
    } as CompositeColumn<T?>

    /**
     * Appends a database-specific column [definition] to this column's SQL in a CREATE TABLE statement.
     *
     * The specified [definition] is appended after the column's name, type, and default value (if any),
     * but before any column constraint definitions. If multiple definition arguments are passed, they
     * will be joined as string representations separated by a single space character.
     */
    fun <T> Column<T>.withDefinition(vararg definition: Any): Column<T> = apply {
        extraDefinitions.addAll(definition)
    }

    /**
     * Transforms a column by specifying transformation functions.
     *
     * Sample:
     * ```kotlin
     * object TestTable : IntIdTable() {
     *     val stringToInteger = integer("stringToInteger")
     *         .transform(wrap = { it.toString() }, unwrap = { it.toInt() })
     * }
     * ```
     *
     * @param Wrapped The type into which the value of the underlying column will be transformed.
     * @param Unwrapped The type of the original column.
     * @param wrap A function to transform from the source type [Unwrapped] to the target type [Wrapped].
     * @param unwrap A function to transform from the target type [Wrapped] to the source type [Unwrapped].
     * @return A new column of type [Wrapped] with the applied transformations.
     */
    fun <Unwrapped : Any, Wrapped : Any> Column<Unwrapped>.transform(
        wrap: (Unwrapped) -> Wrapped,
        unwrap: (Wrapped) -> Unwrapped
    ): Column<Wrapped> = transform(columnTransformer(unwrap, wrap))

    /**
     * Transforms a column by specifying a transformer.
     *
     * Sample:
     * ```kotlin
     * object StringToIntListTransformer : ColumnTransformer<String, List<Int>> {
     *     override fun wrap(value: String): List<Int> {
     *         val result = value.split(",").map { it.toInt() }
     *         return result
     *     }
     *
     *     override fun unwrap(value: List<Int>): String = value.joinToString(",")
     * }
     *
     * object TestTable : IntIdTable() {
     *     val numbers = text("numbers").transform(StringToIntListTransformer)
     * }
     * ```
     *
     * @param Wrapped The type into which the value of the underlying column will be transformed.
     * @param Unwrapped The type of the original column.
     * @param transformer An instance of [ColumnTransformer] to handle the transformations.
     * @return A new column of type [Wrapped] with the applied transformations.
     */
    fun <Unwrapped : Any, Wrapped : Any> Column<Unwrapped>.transform(
        transformer: ColumnTransformer<Unwrapped, Wrapped>
    ): Column<Wrapped> {
        val newColumn = copyWithAnotherColumnType(ColumnWithTransform(this.columnType, transformer)) {
            defaultValueFun = this@transform.defaultValueFun?.let { { transformer.wrap(it()) } }
        }
        return replaceColumn(this, newColumn)
    }

    /**
     * Transforms a nullable column by specifying transformation functions.
     *
     * Sample:
     * ```kotlin
     * object TestTable : IntIdTable() {
     *     val nullableStringToInteger = integer("nullableStringToInteger")
     *         .nullable()
     *         .transform(wrap = { it?.toString() }, unwrap = { it?.toInt() })
     * }
     * ```
     *
     * @param Wrapped The type into which the value of the underlying column will be transformed.
     * @param Unwrapped The type of the original column.
     * @param wrap A function to transform from the source type [Unwrapped] to the target type [Wrapped].
     * @param unwrap A function to transform from the target type [Wrapped] to the source type [Unwrapped].
     * @return A new column of type [Wrapped]`?` with the applied transformations.
     */
    @JvmName("transformNullable")
    fun <Unwrapped : Any, Wrapped : Any> Column<Unwrapped?>.transform(
        wrap: (Unwrapped?) -> Wrapped?,
        unwrap: (Wrapped?) -> Unwrapped?
    ): Column<Wrapped?> = transform(columnTransformer(unwrap, wrap))

    /**
     * Transforms a nullable column by specifying a transformer.
     *
     * Sample:
     * ```kotlin
     * object StringToIntListTransformer : ColumnTransformer<String?, List<Int>?> {
     *     override fun wrap(value: String?): List<Int>? = value?.split(",")?.map { it.toInt() }
     *
     *     override fun unwrap(value: List<Int>): String = value?.joinToString(",")
     * }
     *
     * object TestTable : IntIdTable() {
     *     val numbers = text("numbers").nullable().transform(StringToIntListTransformer)
     * }
     * ```
     *
     * @param Wrapped The type into which the value of the underlying column will be transformed.
     * @param Unwrapped The type of the original column.
     * @param transformer An instance of [ColumnTransformer] to handle the transformations.
     * @return A new column of type [Wrapped]`?` with the applied transformations.
     */
    @JvmName("transformNullable")
    fun <Unwrapped : Any, Wrapped : Any> Column<Unwrapped?>.transform(
        transformer: ColumnTransformer<Unwrapped?, Wrapped?>
    ): Column<Wrapped?> {
        val newColumn = copyWithAnotherColumnType<Wrapped?>(NullableColumnWithTransform(this.columnType, transformer)) {
            defaultValueFun = this@transform.defaultValueFun?.let { { it()?.let { value -> transformer.wrap(value) } } }
        }
        return replaceColumn(this, newColumn)
    }

    /**
     * Applies a special transformation that allows a non-nullable database column
     * to accept and/or return values as `null` on the client side.
     *
     * This transformation does not alter the column's definition in the database,
     * which will still be `NON NULL`. It enables reflecting non-null values
     * from the database as `null` in Kotlin (e.g., converting an empty string from a
     * non-nullable text column, empty lists, negative IDs, etc., to `null`).
     *
     * @param Wrapped The type into which the value of the underlying column will be transformed.
     * @param Unwrapped The type of the original column.
     * @param wrap A function to transform from the source type [Unwrapped] to the target type [Wrapped].
     * @param unwrap A function to transform from the target type [Wrapped] to the source type [Unwrapped].
     * @return A new column of type [Wrapped]`?` with the applied transformations.
     */
    @JvmName("nullTransform")
    fun <Unwrapped : Any, Wrapped : Any> Column<Unwrapped>.nullTransform(
        wrap: (Unwrapped) -> Wrapped?,
        unwrap: (Wrapped?) -> Unwrapped
    ): Column<Wrapped?> = nullTransform(columnTransformer(unwrap, wrap))

    /**
     * Applies a special transformation that allows a non-nullable database column
     * to accept and/or return values as `null` on the client side.
     *
     * This transformation does not alter the column's definition in the database,
     * which will still be `NON NULL`. It enables reflecting non-null values
     * from the database as `null` in Kotlin (e.g., converting an empty string from a
     * non-nullable text column, empty lists, negative IDs, etc., to `null`).
     *
     * @param Wrapped The type into which the value of the underlying column will be transformed.
     * @param Unwrapped The type of the original column.
     * @param transformer An instance of [ColumnTransformer] to handle the transformations.
     * @return A new column of type [Wrapped]`?` with the applied transformations.
     */
    @JvmName("nullTransform")
    fun <Unwrapped : Any, Wrapped : Any> Column<Unwrapped>.nullTransform(
        transformer: ColumnTransformer<Unwrapped, Wrapped?>
    ): Column<Wrapped?> {
        val newColumn = copyWithAnotherColumnType<Wrapped?>(NullableColumnWithTransform(this.columnType, transformer)) {
            defaultValueFun = this@nullTransform.defaultValueFun?.let { { it().let { value -> transformer.wrap(value) } } }
        }
        return replaceColumn(this, newColumn)
    }

    // Indices

    /**
     * Creates an index.
     *
     * @param isUnique Whether the index is unique or not.
     * @param columns Columns that compose the index.
     */
    fun index(isUnique: Boolean = false, vararg columns: Column<*>) {
        index(null, isUnique, *columns)
    }

    /**
     * Creates an index.
     *
     * @param customIndexName Name of the index.
     * @param isUnique Whether the index is unique or not.
     * @param columns Columns that compose the index.
     * @param functions Functions that compose the index.
     * @param indexType A custom index type (e.g., "BTREE" or "HASH").
     * @param filterCondition Index filtering conditions (also known as "partial index") declaration.
     */
    fun index(
        customIndexName: String? = null,
        isUnique: Boolean = false,
        vararg columns: Column<*>,
        functions: List<ExpressionWithColumnType<*>>? = null,
        indexType: String? = null,
        filterCondition: FilterCondition = null
    ) {
        _indices.add(
            Index(
                columns.toList(),
                isUnique,
                customIndexName,
                indexType,
                filterCondition?.invoke(SqlExpressionBuilder),
                functions,
                functions?.let { this }
            )
        )
    }

    /**
     * Creates an index composed by this column only.
     *
     * @param customIndexName Name of the index.
     * @param isUnique Whether the index is unique or not.
     */
    fun <T> Column<T>.index(customIndexName: String? = null, isUnique: Boolean = false): Column<T> =
        apply { table.index(customIndexName, isUnique, this) }

    /**
     * Creates a unique index composed by this column only.
     *
     * @param customIndexName Name of the index.
     */
    fun <T> Column<T>.uniqueIndex(customIndexName: String? = null): Column<T> =
        index(customIndexName, true)

    /**
     * Creates a unique index.
     *
     * @param columns Columns that compose the index.
     * @param filterCondition Index filtering conditions (also known as "partial index") declaration.
     */
    fun uniqueIndex(vararg columns: Column<*>, filterCondition: FilterCondition = null) {
        index(null, true, *columns, filterCondition = filterCondition)
    }

    /**
     * Creates a unique index.
     *
     * @param customIndexName Name of the index.
     * @param columns Columns that compose the index.
     * @param functions Functions that compose the index.
     * @param filterCondition Index filtering conditions (also known as "partial index") declaration.
     */
    fun uniqueIndex(
        customIndexName: String? = null,
        vararg columns: Column<*>,
        functions: List<ExpressionWithColumnType<*>>? = null,
        filterCondition: FilterCondition = null
    ) {
        index(customIndexName, true, *columns, functions = functions, filterCondition = filterCondition)
    }

    /**
     * Creates a composite foreign key.
     *
     * @param from Columns in this referencing child table that compose the foreign key.
     * Their order should match the order of columns in the referenced parent table's primary key.
     * @param target Primary key of the referenced parent table.
     * @param onUpdate [ReferenceOption] when performing update operations.
     * @param onDelete [ReferenceOption] when performing delete operations.
     * @param name Custom foreign key constraint name.
     * @sample org.jetbrains.exposed.v1.tests.shared.ddl.CreateMissingTablesAndColumnsTests.CompositeForeignKeyTable
     */
    fun foreignKey(
        vararg from: Column<*>,
        target: PrimaryKey,
        onUpdate: ReferenceOption? = null,
        onDelete: ReferenceOption? = null,
        name: String? = null
    ) {
        require(from.size == target.columns.size) {
            val fkName = if (name != null) " ($name)" else ""
            "Foreign key$fkName has ${from.size} columns, while referenced primary key (${target.name}) has ${target.columns.size}"
        }
        _foreignKeys.add(ForeignKeyConstraint(from.zip(target.columns).toMap(), onUpdate, onDelete, name))
    }

    /**
     * Creates a composite foreign key.
     *
     * @param references Pairs of child table and parent table columns that compose the foreign key.
     * The first value of each pair should be a column from this referencing child table,
     * with the second value being a column from the referenced parent table.
     * All referencing columns must belong to this table.
     * All referenced columns must belong to the same table.
     * @param onUpdate [ReferenceOption] when performing update operations.
     * @param onDelete [ReferenceOption] when performing delete operations.
     * @param name Custom foreign key constraint name.
     * @sample org.jetbrains.exposed.v1.tests.shared.DDLTests.testCompositeFKReferencingUniqueIndex
     */
    fun foreignKey(
        vararg references: Pair<Column<*>, Column<*>>,
        onUpdate: ReferenceOption? = null,
        onDelete: ReferenceOption? = null,
        name: String? = null
    ) {
        _foreignKeys.add(ForeignKeyConstraint(references.toMap(), onUpdate, onDelete, name))
    }

    // Check constraints

    /**
     * Creates a check constraint in this column.
     * @param name The name to identify the constraint, optional. Must be **unique** (case-insensitive) to this table,
     * otherwise, the constraint will not be created. All names are [trimmed][String.trim], blank names are ignored and
     * the database engine decides the default name.
     * @param op The expression against which the newly inserted values will be compared.
     */
    fun <T> Column<T>.check(name: String = "", op: SqlExpressionBuilder.(Column<T>) -> Op<Boolean>): Column<T> = apply {
        if (name.isEmpty() || table.checkConstraints.none { it.first.equals(name, true) }) {
            table.checkConstraints.add(name to SqlExpressionBuilder.op(this))
        } else {
            exposedLogger
                .warn("A CHECK constraint with name '$name' was ignored because there is already one with that name")
        }
    }

    /**
     * Creates a check constraint in this table.
     * @param name The name to identify the constraint, optional. Must be **unique** (case-insensitive) to this table,
     * otherwise, the constraint will not be created. All names are [trimmed][String.trim], blank names are ignored and
     * the database engine decides the default name.
     * @param op The expression against which the newly inserted values will be compared.
     */
    fun check(name: String = "", op: SqlExpressionBuilder.() -> Op<Boolean>) {
        if (name.isEmpty() || checkConstraints.none { it.first.equals(name, true) }) {
            checkConstraints.add(name to SqlExpressionBuilder.op())
        } else {
            exposedLogger
                .warn("A CHECK constraint with name '$name' was ignored because there is already one with that name")
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

    private fun <T> IColumnType<T>.cloneAsBaseType(): IColumnType<T> = ((this as? AutoIncColumnType)?.delegate ?: this).clone()

    private fun <T> Column<T>.cloneWithAutoInc(idSeqName: String?): Column<T> = when (columnType) {
        is AutoIncColumnType -> this
        is ColumnType -> {
            val fallbackSequenceName = fallbackSequenceName(tableName = tableName, columnName = name)
            this.withColumnType(
                AutoIncColumnType(columnType, idSeqName, fallbackSequenceName)
            )
        }

        else -> error("Unsupported column type for auto-increment $columnType")
    }

    private fun <T> Column<T>.cloneWithAutoInc(sequence: Sequence): Column<T> = when (columnType) {
        is AutoIncColumnType -> this
        is ColumnType -> this.withColumnType(AutoIncColumnType(columnType, sequence))
        else -> error("Unsupported column type for auto-increment $columnType")
    }

    // DDL statements

    @OptIn(InternalApi::class)
    internal fun primaryKeyConstraint(): String? {
        return primaryKey?.let { primaryKey ->
            val tr = CoreTransactionManager.currentTransaction()
            val constraint = tr.db.identifierManager.cutIfNecessaryAndQuote(primaryKey.name)
            return primaryKey.columns
                .joinToString(prefix = "CONSTRAINT $constraint PRIMARY KEY (", postfix = ")", transform = tr::identity)
        }
    }

    override fun createStatement(): List<String> {
        @OptIn(InternalApi::class)
        val addForeignKeysInAlterPart = TableUtils.checkCycle(this) && currentDialect !is SQLiteDialect

        val foreignKeyConstraints = foreignKeys

        @OptIn(InternalApi::class)
        val createTable = buildString {
            append("CREATE TABLE ")
            if (currentDialect.supportsIfNotExists) {
                append("IF NOT EXISTS ")
            }
            append(CoreTransactionManager.currentTransaction().identity(this@Table))

            if (columns.isNotEmpty()) {
                columns.joinTo(this, prefix = " (") { column ->
                    column.descriptionDdl(false)
                }

                if (columns.any { it.isPrimaryConstraintWillBeDefined }) {
                    primaryKeyConstraint()?.let { append(", $it") }
                }

                if (!addForeignKeysInAlterPart && foreignKeyConstraints.isNotEmpty()) {
                    foreignKeyConstraints.joinTo(this, prefix = ", ", separator = ", ") { it.foreignKeyPart }
                }

                if (checkConstraints.isNotEmpty()) {
                    checkConstraints().map { it.checkPart }.ifEmpty { null }?.joinTo(this, prefix = ", ")
                }

                append(")")
            }
        }

        val createConstraint = if (addForeignKeysInAlterPart) {
            foreignKeyConstraints.flatMap { it.createStatement() }
        } else {
            emptyList()
        }

        return createAutoIncColumnSequence() + createTable + createConstraint
    }

    private fun createAutoIncColumnSequence(): List<String> {
        return autoIncColumn?.autoIncColumnType?.sequence?.createStatement().orEmpty()
    }

    override fun modifyStatement(): List<String> =
        throw UnsupportedOperationException("Use modify on columns and indices")

    override fun dropStatement(): List<String> {
        @OptIn(InternalApi::class)
        val dropTable = buildString {
            append("DROP TABLE ")
            if (currentDialect.supportsIfNotExists) {
                append("IF EXISTS ")
            }
            append(CoreTransactionManager.currentTransaction().identity(this@Table))
            if (currentDialectIfAvailable is OracleDialect) {
                append(" CASCADE CONSTRAINTS")
            } else if (currentDialectIfAvailable is PostgreSQLDialect && TableUtils.checkCycle(this@Table)) {
                append(" CASCADE")
            }
        }

        val dropSequence = autoIncColumn?.autoIncColumnType?.sequence?.dropStatement().orEmpty()

        return listOf(dropTable) + dropSequence
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Table) return false

        if (tableName != other.tableName) return false

        return true
    }

    override fun hashCode(): Int = tableName.hashCode()

    /**
     * Represents a special dummy `DUAL` table that is accessible by all users.
     *
     * This can be useful when needing to execute queries that do not rely on a specific table object.
     * **Note:** `DUAL` tables are only automatically supported by Oracle. Please check the documentation.
     */
    object Dual : Table("dual")
}

/** Returns the list of tables to which the columns in this column set belong. */
fun ColumnSet.targetTables(): List<Table> = when (this) {
    is Alias<*> -> listOf(this.delegate)
    is QueryAlias -> this.query.set.source.targetTables()
    is Table -> listOf(this)
    is Join -> this.table.targetTables() + this.joinParts.flatMap { it.joinPart.targetTables() }
    else -> error("No target provided for update")
}

private fun String.isAlreadyQuoted(): Boolean =
    listOf("\"", "'", "`").any { quoteString ->
        startsWith(quoteString) && endsWith(quoteString)
    }

internal fun fallbackSequenceName(tableName: String, columnName: String): String {
    val q = if (tableName.contains('.')) "\"" else ""
    return "$q${tableName.replace("\"", "")}_${columnName}_seq$q"
}

private fun <T> Column<T>.unquotedName() = name.trim('\"', '\'', '`')
