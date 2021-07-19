package org.jetbrains.exposed.sql

open class SchemaTable<T : Table>(
    val scheme: Schema,
    val delegate: T,
    val references: Map<Column<*>, SchemaTable<*>>? = null
) : Table() {

    init {
        references?.keys?.forEach {
            if (!delegate.columns.contains(it)) {
                throw Exception("Column ${it.name} doesn't belong to table ${delegate.tableName}")
            }
        }
    }

    override val tableName: String = "${scheme.name}.${delegate.tableNameWithoutScheme}"
    override val columns: List<SchemaTableColumn<*>> = delegate.columns.map { (it as Column<Comparable<*>>).clone() }
    override val primaryKey: PrimaryKey? = delegate.primaryKey?.clone()

    private fun PrimaryKey.clone(): PrimaryKey? {
        val resolvedPK = columns.map { (it as Column<Comparable<*>>).clone() }.toTypedArray()

        val primaryKey = PrimaryKey(*resolvedPK, name = name)
        return primaryKey
    }

    private fun <U : Comparable<U>> Column<U>.clone(): SchemaTableColumn<U> {
        val column = SchemaTableColumn<U>(this@SchemaTable, name, this, columnType)
        val resolvedReferee = findReferee(referee)
        if (resolvedReferee != null) {
            column.foreignKey = foreignKey?.copy(target = resolvedReferee)
        }
        return column
    }

    fun <U : Comparable<U>> Column<U>.findReferee(referee: Column<*>?): Column<*>? =
        if (references == null || references.isEmpty() || referee == null) {
            referee
        } else {
            val reference = references[this]

            reference?.columns?.single {
                it.idColumn === referee
            }
        }

    operator fun <T> get(column: Column<T>): SchemaTableColumn<T> {
        return columns.single {
            it.idColumn == column
        } as SchemaTableColumn<T>
    }
}

/**
 * @sample org.jetbrains.exposed.sql.tests.shared.SchemaTests
 *
 * By default, the table references tables in the default schema.
 * If you want to join with tables from other schemas, you can pass them
 * in the [references] parameters.
 *
 * example : tableB.withSchema(schema1, idColumn to tableA.withSchema(schema2))
 * will create tableB in schema1 that references tableA in schema2
 *
 * @param schema the schema of the table
 * @param references tables to make join with. Order of tables is not important.
 */
fun <T : Table> T.withSchema(schema: Schema, vararg references: Pair<Column<*>, SchemaTable<*>>): SchemaTable<T> =
    SchemaTable(schema, this, references.toMap())
