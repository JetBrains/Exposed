package org.jetbrains.exposed.v1.plugin.core.tables.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.plugin.core.migration.MigrationLogger
import org.jetbrains.exposed.v1.plugin.core.tables.DateTimeColumnType
import org.jetbrains.exposed.v1.plugin.core.tables.UuidColumnType
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.ColumnDefinition
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.ColumnFunctions
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.mappedKotlinType
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.toColumnDefinition
import org.jetbrains.exposed.v1.plugin.core.tables.mapping.toMappedColumnDefinition
import org.jetbrains.exposed.v1.plugin.core.tables.schema.ColumnInfo
import org.jetbrains.exposed.v1.plugin.core.tables.schema.ForeignKeyInfo
import org.jetbrains.exposed.v1.plugin.core.tables.schema.TableMetadata
import org.jetbrains.exposed.v1.plugin.core.tables.schema.TableReference
import org.jetbrains.exposed.v1.plugin.core.tables.schema.dialect.DialectSyntax
import org.jetbrains.exposed.v1.plugin.core.tables.toPropertyName
import org.jetbrains.exposed.v1.plugin.core.tables.warn

/**
 * What a table's generated code may refer to in other generated tables, which usually involves their object names,
 * their [TableKind], and which of their columns become properties. This is built once, before any table is generated,
 * so that a foreign key can be rendered as a reference to another table's property regardless of generation order.
 */
@OptIn(InternalApi::class)
internal class GenerationContext(
    tables: List<TableMetadata>,
    private val objectNames: Map<TableReference, String>,
    /** Interprets the strings and codes that this support reads. */
    val syntax: DialectSyntax,
    uuidColumnType: UuidColumnType,
    /** The date-time module whose functions all date-time columns, and their defaults, are generated with. */
    val dateTimeColumnType: DateTimeColumnType,
    /** Whether tables with a primary key are generated as `IdTable` subclasses rather than plain `Table` objects. */
    private val generateIdTables: Boolean,
    private val logger: MigrationLogger,
) {
    /** The referenced column of a foreign key rendered as the generated property that declares it. */
    data class Target(
        /** `property` within the same object, or `Object.property` for another table. */
        val code: CodeBlock,

        /** Whether the referenced column's code is configured by a mapping, so that its Kotlin type is unknown. */
        val isMapped: Boolean,

        /**
         * When the referenced column is the single `id` of an `IdTable`, this value holds that table's object name and
         * key type. This ensures that the key can be declared with `reference(name, Object)`.
         */
        val idTable: IdTableTarget?,
    )

    /** An `IdTable` that a foreign key points at, for `reference(name, Object)`. */
    data class IdTableTarget(val objectName: String, val idType: TypeName)

    private val tablesByName: Map<Pair<String, String>, TableMetadata> = tables.associateBy {
        it.reference.schema to it.reference.tableName
    }

    /**
     * The definition of every column of every table. This is resolved once, either at the configured mapping when there
     * is one, at the generated mapping, or produces `null` when the column is skipped.
     */
    private val definitions: Map<ColumnInfo, ColumnDefinition?> = tables.flatMap { it.columns }.associateWith { column ->
        column.mapping?.let { column.metadata.toMappedColumnDefinition(it, dateTimeColumnType) }
            ?: column.metadata.toColumnDefinition(syntax, uuidColumnType, dateTimeColumnType, column.enumeration)
    }

    private val generatedColumns: Map<Pair<String, String>, Map<String, ColumnInfo>> = tables.associate { table ->
        (table.reference.schema to table.reference.tableName) to table.columns.filter { it.isGenerated() }.associateBy { it.name }
    }

    private val kinds: Map<TableReference, TableKind> = tables.associate { it.reference to decideKind(it) }

    /** What the generated object of [table] extends. */
    fun kindOf(table: TableMetadata): TableKind = kinds.getValue(table.reference)

    /** The definition the generator produces for [column], or `null` when the column is skipped. */
    fun definitionOf(column: ColumnInfo): ColumnDefinition? = definitions.getValue(column)

    private fun ColumnInfo.isGenerated(): Boolean = definitionOf(this) != null

    /**
     * Resolves the referenced columns of [key], declared in [from], to the properties that declare them in key order.
     * This ails when the referenced table is not among the generated tables or when a referenced column is not generated.
     */
    fun resolveTargets(key: ForeignKeyInfo, from: TableMetadata): Result<List<Target>> {
        val targetKey = key.targetSchema to key.targetTable
        val targetTable = tablesByName[targetKey]
            ?: return Result.failure(UnresolvedTargetException("table '${key.targetSchema}.${key.targetTable}' is not generated"))
        val targetColumns = generatedColumns.getValue(targetKey)
        val targetKind = kinds.getValue(targetTable.reference)
        val targetObjectName = objectNames.getValue(targetTable.reference)
        val targets = key.targetColumnNames.map { targetColumnName ->
            val targetColumn = targetColumns[targetColumnName]
                ?: return Result.failure(UnresolvedTargetException("column '$targetColumnName' of table '${targetTable.name}' is not generated"))
            val property = propertyNameOf(targetColumnName, targetKind)
            val code = if (targetTable.reference == from.reference) {
                CodeBlock.of("%N", property)
            } else {
                CodeBlock.of("%N.%N", targetObjectName, property)
            }
            val idTable = targetKind.singleIdType
                ?.takeIf { targetKind.keyColumnName() == targetColumnName }
                ?.let { IdTableTarget(targetObjectName, it) }
            Target(code, isMapped = targetColumn.mapping != null, idTable = idTable)
        }
        return Result.success(targets)
    }

    fun propertyNameOf(columnName: String, kind: TableKind): String =
        if (kind is TableKind.GenericId && kind.keyColumnName == columnName) "id" else columnName.toPropertyName()

    private fun TableKind.keyColumnName(): String? = when (this) {
        is TableKind.ExactId -> keyColumnName
        is TableKind.GenericId -> keyColumnName
        else -> null
    }

    /** Why a foreign key target could not be resolved. */
    class UnresolvedTargetException(message: String) : RuntimeException(message)

    /**
     * Decides what [table] extends. A concrete `IdTable` subclass is used only when the key column has nothing to say
     * beyond what the subclass declares (no reference, index, default, generated value or mapping); otherwise the
     * generic `IdTable<T>` keeps the column in the body. A key that includes a column the generator cannot type, or
     * does not generate, falls back to a plain `Table` with a warning.
     */
    private fun decideKind(table: TableMetadata): TableKind {
        if (!generateIdTables) return TableKind.Plain
        val primaryKey = table.primaryKey ?: return TableKind.Plain
        val keyColumns = primaryKey.columnNames.map { name -> table.columns.first { it.name == name } }
        val ungenerated = keyColumns.filterNot { it.isGenerated() }
        if (ungenerated.isNotEmpty()) {
            logger.warn(
                "Table '${table.name}' is generated as a plain Table rather than an IdTable, because its primary key includes " +
                    "${ungenerated.joinToString { "'${it.name}'" }}, which is not generated"
            )
            return TableKind.Plain
        }
        if (keyColumns.size > 1) return TableKind.CompositeId(primaryKey.columnNames)

        val key = keyColumns.single()
        val definition = requireNotNull(definitionOf(key))
        val idType = definition.typeName ?: mappedKotlinType(definition.function, dateTimeColumnType)
        if (idType == null) {
            logger.warn(
                "Table '${table.name}' is generated as a plain Table rather than an IdTable, because the Kotlin type of its mapped " +
                    "key column '${key.name}' cannot be told from the mapping"
            )
            return TableKind.Plain
        }
        val concreteClass = key.exactIdTableClass(definition)
            ?: return TableKind.GenericId(key.name, idType)
        primaryKey.customName?.let { name ->
            logger.lifecycle(
                "Table '${table.name}' is generated as a ${concreteClass.simpleName}, whose primary key is unnamed; the key name '$name' " +
                    "is not kept (Exposed's migration utilities never compare primary key names)"
            )
        }
        return TableKind.ExactId(concreteClass, key.name, key.sequenceName, idType)
    }

    private fun ColumnInfo.exactIdTableClass(definition: ColumnDefinition): ClassName? {
        val hasOwnDefault = reportedDefault != null && sequenceName == null
        val keyHasMoreToSay = mapping != null || foreignKey != null || indices.isNotEmpty() ||
            hasOwnDefault || sequenceParameters != null || generationExpression != null
        if (keyHasMoreToSay) return null
        val isAutoIncrement = metadata.autoIncrement || sequenceName != null
        return when (definition.function) {
            ColumnFunctions.INTEGER if isAutoIncrement -> ClassName(DAO_ID_PACKAGE, "IntIdTable")
            ColumnFunctions.LONG if isAutoIncrement -> ClassName(DAO_ID_PACKAGE, "LongIdTable")
            ColumnFunctions.UUID -> ClassName(DAO_ID_PACKAGE, "UuidTable")
            ColumnFunctions.JAVA_UUID -> ClassName("$DAO_ID_PACKAGE.java", "UUIDTable")
            else -> null
        }
    }

    /**
     * The groups of at least two tables whose generated references lead back to themselves. Such tables are Kotlin
     * objects that reference each other while initializing, which fails at first use. Exposed has no deferred
     * reference, so the user must remove one of the references. Note that a self-reference is not a cycle.
     */
    val referenceCycles: List<List<TableMetadata>> by lazy {
        val tables = tablesByName.values.toList()
        val edges = tables.associate { table -> table.reference to table.referencedTables() }
        CycleFinder(tables, edges).components().filter { it.size > 1 }
    }

    /** The other tables of the reference cycle that [table] belongs to, or `null` when it belongs to none. */
    fun cycleWith(table: TableMetadata): List<TableMetadata>? = referenceCycles.firstOrNull { cycle ->
        cycle.any { it.reference == table.reference }
    }?.filter { it.reference != table.reference }

    /** The other generated tables that this table's rendered references point at. */
    private fun TableMetadata.referencedTables(): List<TableMetadata> {
        val keys = columns.mapNotNull { it.foreignKey } + compositeForeignKeys
        return keys
            .filter { key -> resolveTargets(key, this).isSuccess }
            .mapNotNull { key -> tablesByName[key.targetSchema to key.targetTable] }
            .filter { it.reference != reference }
            .distinctBy { it.reference }
    }

    private class CycleFinder(
        private val tables: List<TableMetadata>,
        private val edges: Map<TableReference, List<TableMetadata>>,
    ) {
        private val indices = hashMapOf<TableReference, Int>()
        private val lowLinks = hashMapOf<TableReference, Int>()
        private val stack = ArrayDeque<TableMetadata>()
        private val onStack = hashSetOf<TableReference>()
        private val components = mutableListOf<List<TableMetadata>>()

        fun components(): List<List<TableMetadata>> {
            tables.forEach { table ->
                if (table.reference !in indices) visit(table)
            }
            return components
        }

        private fun visit(table: TableMetadata) {
            val index = indices.size
            indices[table.reference] = index
            lowLinks[table.reference] = index
            stack.addLast(table)
            onStack.add(table.reference)

            edges.getValue(table.reference).forEach { target ->
                if (target.reference !in indices) {
                    visit(target)
                    lowLinks[table.reference] = minOf(lowLinks.getValue(table.reference), lowLinks.getValue(target.reference))
                } else if (target.reference in onStack) {
                    lowLinks[table.reference] = minOf(lowLinks.getValue(table.reference), indices.getValue(target.reference))
                }
            }

            if (lowLinks[table.reference] == indices[table.reference]) {
                val component = mutableListOf<TableMetadata>()
                do {
                    val member = stack.removeLast()
                    onStack.remove(member.reference)
                    component.add(member)
                } while (member.reference != table.reference)
                components.add(component.reversed())
            }
        }
    }
}

internal const val DAO_ID_PACKAGE: String = "org.jetbrains.exposed.v1.core.dao.id"

/**
 * What class a generated table object extends, which is decided from its primary key once the columns are known.
 */
internal sealed interface TableKind {
    /** A plain [org.jetbrains.exposed.v1.core.Table], with the primary key declared as `override val primaryKey`. */
    data object Plain : TableKind

    /** One of the [org.jetbrains.exposed.v1.core.dao.id.IdTable] subclasses */
    data class ExactId(
        val className: ClassName,
        val keyColumnName: String,
        /** The sequence the key takes its values from, for `IntIdTable(name, columnName, sequenceName)`, or `null`. */
        val sequenceName: String?,
        /** The Kotlin type of the key, for `EntityID<T>` references to this table. */
        val idType: TypeName,
    ) : TableKind

    /**
     * An [org.jetbrains.exposed.v1.core.dao.id.IdTable] with the key column declared as `override val id`,
     * for a key that no concrete subclass fits.
     */
    data class GenericId(
        val keyColumnName: String,
        /** The Kotlin type of the key, for `EntityID<T>` references to this table. */
        val idType: TypeName,
    ) : TableKind

    /**
     * A [org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable], with every key column declared through `entityId()`
     * under its own property name.
     */
    data class CompositeId(val keyColumnNames: List<String>) : TableKind
}

internal val TableKind.singleIdType: TypeName?
    get() = when (this) {
        is TableKind.ExactId -> idType
        is TableKind.GenericId -> idType
        else -> null
    }

internal val TableKind.entityIdColumnNames: List<String>
    get() = when (this) {
        is TableKind.GenericId -> listOf(keyColumnName)
        is TableKind.CompositeId -> keyColumnNames
        else -> emptyList()
    }
