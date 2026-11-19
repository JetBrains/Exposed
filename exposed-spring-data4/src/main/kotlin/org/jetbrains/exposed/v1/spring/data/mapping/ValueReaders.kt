package org.jetbrains.exposed.v1.spring.data.mapping

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.select

/** Reads the value of one property from the rows converted together, see [RowConverter.convertAll]. */
internal sealed class ValueReader {
    /** The columns of the converted table the reader needs. */
    abstract val columns: List<Column<*>>

    /** Loads what [rows] need from other tables, and returns the reader of the value for one of them. */
    abstract fun prepare(rows: List<ResultRow>): (ResultRow) -> Any?
}

/** The value of [column] itself (the plain value for `EntityID`s). */
internal class ColumnReader(private val column: Column<*>) : ValueReader() {
    override val columns: List<Column<*>> = listOf(column)

    override fun prepare(rows: List<ResultRow>): (ResultRow) -> Any? = { row -> row[column].unwrapEntityId() }
}

/** The row referenced by the foreign key [column] (`null` if the column is), loaded with one query for all rows and converted by [converter]. */
internal class ReferenceReader(
    private val column: Column<*>,
    private val referee: Column<*>,
    private val converter: RowConverter<*>,
) : ValueReader() {
    override val columns: List<Column<*>> = listOf(column)

    override fun prepare(rows: List<ResultRow>): (ResultRow) -> Any? {
        val loaded = loadByKey(referee, rows.mapNotNull { it[column].unwrapEntityId() }, converter).toMap()
        return { row -> row[column].unwrapEntityId()?.let { loaded[it] } }
    }
}

/** The rows of a to-many relation for the value of [parentKey], as a `List` or a `Set`. [load] returns `(parent key, element)` pairs. */
internal class CollectionReader(
    private val parentKey: Column<*>,
    private val isSet: Boolean,
    private val load: (List<Any>) -> List<Pair<Any, Any>>,
) : ValueReader() {
    override val columns: List<Column<*>> = listOf(parentKey)

    override fun prepare(rows: List<ResultRow>): (ResultRow) -> Any? {
        val keys = rows.mapNotNull { it[parentKey].unwrapEntityId() }.distinct()
        val elements = if (keys.isEmpty()) emptyMap() else load(keys).groupBy({ it.first }, { it.second })
        return { row ->
            val found = row[parentKey].unwrapEntityId()?.let { elements[it] }.orEmpty()
            if (isSet) LinkedHashSet(found) else found
        }
    }
}

/**
 * Selects the rows of the table of [key] whose [key] is one of [keys] (in chunks), converts them with [converter]
 * and returns them as `(key value, converted row)` pairs, ordered by [orderBy].
 */
@Suppress("UNCHECKED_CAST")
internal fun loadByKey(key: Column<*>, keys: Collection<Any>, converter: RowConverter<*>, orderBy: List<Column<*>> = emptyList()): List<Pair<Any, Any>> =
    keys.distinct().chunked(IN_LIST_CHUNK_SIZE).flatMap { chunk ->
        val column = key as Column<Any?>
        val query = key.table.select((converter.columns + key).distinct()).where { column inList chunk.map { column.toDbValue(it) } }
        orderBy.forEach { query.orderBy(it) }
        val rows = query.toList()
        rows.map { checkNotNull(it[key].unwrapEntityId()) }.zip(converter.convertAll(rows))
    }

/** Loads the elements of a many-to-many [relation] for the parent [keys]: the links of the join table, then the referenced rows. */
@Suppress("UNCHECKED_CAST")
internal fun loadThroughJoinTable(relation: JoinRelation, keys: List<Any>, converter: RowConverter<*>): List<Pair<Any, Any>> {
    val links = keys.chunked(IN_LIST_CHUNK_SIZE).flatMap { chunk ->
        val query = relation.table.select(relation.foreignKey, relation.targetKey)
            .where { relation.foreignKey inList chunk.map { relation.foreignKey.toDbValue(it) } }
        relation.table.primaryKey?.columns?.forEach { query.orderBy(it) }
        query.mapNotNull { row ->
            val target = row[relation.targetKey].unwrapEntityId() ?: return@mapNotNull null
            checkNotNull(row[relation.foreignKey].unwrapEntityId()) to target
        }
    }
    val targets = loadByKey(relation.referee, links.map { it.second }, converter).toMap()
    return links.mapNotNull { (parent, target) -> targets[target]?.let { parent to it } }
}

// Oracle allows at most 1000 values in an IN list
internal const val IN_LIST_CHUNK_SIZE = 1000
