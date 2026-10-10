package org.jetbrains.exposed.v1.core

import org.jetbrains.exposed.v1.core.transactions.currentTransaction

/** A named query result that can be referenced as a [ColumnSet] in another query. */
class CommonTableExpression internal constructor(
    /** The SQL identifier used to reference this common table expression. */
    val name: String,
    outputFields: List<Expression<*>>,
    /** Whether this common table expression is recursive. */
    val recursive: Boolean,
) : ColumnSet() {
    private val referenceTable = CommonTableExpressionReference(name)
    private val outputMappings = outputFields.mapIndexed { index, expression ->
        expression.toCteOutputMapping(index)
    }
    private var physicalTargets: List<Table> = emptyList()
    private var storedDefinition: AbstractQuery<*>? = null

    /** The snapshotted query that defines this common table expression. */
    internal val definition: AbstractQuery<*>
        get() = checkNotNull(storedDefinition) { "CTE '$name' definition has not been initialized" }

    internal val outputNames: List<String> = outputMappings.map { it.name }

    override val fields: List<Expression<*>> = outputMappings.map { it.output }

    override val columns: List<Column<*>> = fields.filterIsInstance<Column<*>>()

    init {
        require(name.isNotBlank()) { "CTE name must not be blank" }
        require(outputFields.isNotEmpty()) { "CTE '$name' must declare at least one output field" }
    }

    override fun describe(s: Transaction, queryBuilder: QueryBuilder) {
        queryBuilder.requireCommonTableExpressionAttached(this)
        queryBuilder.append(s.db.identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(name))
    }

    override fun join(
        otherTable: ColumnSet,
        joinType: JoinType,
        onColumn: Expression<*>?,
        otherColumn: Expression<*>?,
        lateral: Boolean,
        additionalConstraint: (() -> Op<Boolean>)?,
    ): Join = Join(this, otherTable, joinType, onColumn, otherColumn, lateral, additionalConstraint)

    override fun innerJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.INNER)

    override fun leftJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.LEFT)

    override fun rightJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.RIGHT)

    override fun fullJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.FULL)

    override fun crossJoin(otherTable: ColumnSet): Join = Join(this, otherTable, JoinType.CROSS)

    /** Returns the CTE field corresponding to [original]. */
    operator fun <T> get(original: Column<T>): Column<T> = get(original as Expression<T>) as? Column<T>
        ?: error("CTE '$name' field for $original is not a column")

    /** Returns the CTE field corresponding to [original]. */
    operator fun <T> get(original: Expression<T>): Expression<T> {
        val matches = outputMappings.filter { original in it.sources }
        check(matches.size <= 1) {
            "Field $original is represented more than once in CTE '$name'; use an explicitly aliased output field"
        }
        if (matches.isEmpty() && original is CompositeColumn<*>) {
            error("Composite field $original is expanded in CTE '$name'; use its component columns instead")
        }
        @Suppress("UNCHECKED_CAST")
        return matches.singleOrNull()?.output as? Expression<T>
            ?: error("Field $original is not part of CTE '$name' output")
    }

    /** Returns the typed CTE field corresponding to [original]. */
    operator fun <T> get(original: ExpressionWithColumnType<T>): ExpressionWithColumnType<T> =
        get(original as Expression<T>) as? ExpressionWithColumnType<T>
            ?: error("CTE '$name' field for $original has no column type")

    @OptIn(InternalApi::class)
    internal fun initialize(definition: AbstractQuery<*>, snapshotDefinition: Boolean = true) {
        check(storedDefinition == null) { "CTE '$name' definition is already initialized" }
        val snapshot = if (snapshotDefinition) definition.snapshotForCte() else definition
        snapshot.validateAsCteDefinition(name)
        require(snapshot.set.realFields.size == outputMappings.size) {
            "CTE '$name' declares ${outputMappings.size} fields but its definition returns ${snapshot.set.realFields.size} fields"
        }
        validateTypeCompatibility(snapshot.set.realFields)
        storedDefinition = snapshot
        physicalTargets = snapshot.targets
    }

    internal fun targetTables(): List<Table> = physicalTargets

    internal fun validatePhysicalSchema() {
        val physicalFields = definition.set.realFields
        require(physicalFields.size == outputMappings.size) {
            "CTE '$name' declares ${outputMappings.size} fields but its definition renders ${physicalFields.size} fields"
        }
        validateTypeCompatibility(physicalFields)
    }

    private fun validateTypeCompatibility(physicalFields: List<Expression<*>>) {
        outputMappings.zip(physicalFields).forEachIndexed { index, (mapping, physicalField) ->
            val declaredType = (mapping.output as? ExpressionWithColumnType<*>)?.columnType ?: return@forEachIndexed
            val actualType = (physicalField as? ExpressionWithColumnType<*>)?.columnType ?: return@forEachIndexed
            val declaredFamily = declaredType.cteTypeFamily()
            val actualFamily = actualType.cteTypeFamily()
            require(declaredFamily == CteTypeFamily.UNKNOWN || actualFamily == CteTypeFamily.UNKNOWN || declaredFamily == actualFamily) {
                "CTE '$name' field ${index + 1} declares ${declaredType::class.simpleName} " +
                    "but its definition returns ${actualType::class.simpleName}"
            }
        }
    }

    private fun Expression<*>.toCteOutputMapping(index: Int): OutputMapping {
        val outputName = stableCteOutputName()
            ?: error("CTE '$name' field ${index + 1} has no stable name; alias the expression before creating the CTE")

        val sources = buildList {
            add(this@toCteOutputMapping)
            if (this@toCteOutputMapping is IExpressionAlias<*>) add(delegate)
            if (this@toCteOutputMapping is CastToJson<*>) add(expression)
        }
        val sourceColumn = sources.filterIsInstance<Column<*>>().firstOrNull()
        val output = when {
            sourceColumn != null -> sourceColumn.toCteColumn(outputName)
            this is ExpressionWithColumnType<*> -> toCteTypedExpression(outputName)
            else -> CteOutputExpression<Any?>(name, outputName)
        }
        return OutputMapping(outputName, sources, output)
    }

    @Suppress("UNCHECKED_CAST")
    private fun Column<*>.toCteColumn(outputName: String): Column<*> =
        Column<Any?>(referenceTable, outputName, columnType as IColumnType<Any>)

    @Suppress("UNCHECKED_CAST")
    private fun ExpressionWithColumnType<*>.toCteTypedExpression(outputName: String): ExpressionWithColumnType<*> =
        CteOutputExpressionWithColumnType<Any?>(name, outputName, columnType as IColumnType<Any>)

    private data class OutputMapping(
        val name: String,
        val sources: List<Expression<*>>,
        val output: Expression<*>,
    )
}

internal class CommonTableExpressionReference(private val cteName: String) : Table() {
    override val tableName: String
        get() = cteName

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

/** Creates an ordinary common table expression from this query. */
@OptIn(InternalApi::class)
fun AbstractQuery<*>.asCte(name: String): CommonTableExpression {
    val snapshot = snapshotForCte()
    snapshot.validateAsCteDefinition(name)
    val fields = snapshot.set.realFields
    fields.forEachIndexed { index, _ ->
        require(!snapshot.isGeneratedCteField(index)) {
            "CTE '$name' field ${index + 1} uses a generated alias; alias the expression explicitly before creating the CTE"
        }
    }
    return CommonTableExpression(name, fields, recursive = false).also { it.initialize(snapshot, snapshotDefinition = false) }
}

/** Creates a recursive common table expression with an explicit output schema. */
fun recursiveCte(
    name: String,
    outputFields: List<Expression<*>>,
    definition: (self: CommonTableExpression) -> AbstractQuery<*>,
): CommonTableExpression {
    val cte = CommonTableExpression(name, outputFields, recursive = true)
    cte.initialize(definition(cte))
    return cte
}

private interface CteNamedExpression {
    val cteOutputName: String
}

private class CteOutputExpression<T>(
    private val cteName: String,
    override val cteOutputName: String,
) : Expression<T>(), CteNamedExpression {
    @OptIn(InternalApi::class)
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val identifierManager = currentTransaction().db.identifierManager
        append(identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(cteName))
        append('.')
        append(identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(cteOutputName))
    }
}

private class CteOutputExpressionWithColumnType<T>(
    private val cteName: String,
    override val cteOutputName: String,
    override val columnType: IColumnType<T & Any>,
) : ExpressionWithColumnType<T>(), CteNamedExpression {
    @OptIn(InternalApi::class)
    override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
        val identifierManager = currentTransaction().db.identifierManager
        append(identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(cteName))
        append('.')
        append(identifierManager.quoteIdentifierWhenWrongCaseOrNecessary(cteOutputName))
    }
}

private fun Expression<*>.stableCteOutputName(): String? = when (this) {
    is Column<*> -> name
    is IExpressionAlias<*> -> alias
    is CastToJson<*> -> (expression as? Column<*>)?.name
    is CteNamedExpression -> cteOutputName
    else -> null
}

private enum class CteTypeFamily {
    BOOLEAN,
    NUMERIC,
    STRING,
    BINARY,
    UUID,
    DATE_TIME,
    UNKNOWN,
}

private fun IColumnType<*>.cteTypeFamily(): CteTypeFamily = when (this) {
    is AutoIncColumnType<*> -> delegate.cteTypeFamily()
    is EntityIDColumnType<*> -> idColumn.columnType.cteTypeFamily()
    is BooleanColumnType -> CteTypeFamily.BOOLEAN
    is ByteColumnType,
    is UByteColumnType,
    is ShortColumnType,
    is UShortColumnType,
    is IntegerColumnType,
    is UIntegerColumnType,
    is LongColumnType,
    is ULongColumnType,
    is FloatColumnType,
    is DoubleColumnType,
    is DecimalColumnType -> CteTypeFamily.NUMERIC
    is CharacterColumnType,
    is StringColumnType -> CteTypeFamily.STRING
    is BasicBinaryColumnType,
    is BlobColumnType -> CteTypeFamily.BINARY
    is BasicUuidColumnType<*> -> CteTypeFamily.UUID
    is IDateColumnType -> CteTypeFamily.DATE_TIME
    else -> CteTypeFamily.UNKNOWN
}
