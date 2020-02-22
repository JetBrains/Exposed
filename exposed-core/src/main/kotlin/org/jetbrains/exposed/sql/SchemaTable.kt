package org.jetbrains.exposed.sql

open class SchemaTable<T:Table>(val scheme: String, val table: T, val references: List<SchemaTable<*>>? = null) : Table() {
    override val tableName: String = "$scheme.${table.tableName}"
    override val columns: List<Column<*>> = table.columns.map { it.clone() }
    override val primaryKey: PrimaryKey? = table.primaryKey?.clone()

    private fun PrimaryKey.clone(): PrimaryKey? {
        val resolvedPK = columns.map { it.clone() }.toTypedArray()

        val primaryKey = PrimaryKey(*resolvedPK , name = name)
        return primaryKey
    }

    private fun <U : Any?> Column<U>.clone(): Column<U> {
        val column= Column<U>(this@SchemaTable , name, columnType)
        column.referee = findReferee(referee)
        return column
    }

    fun findReferee(referee: Column<*>?): Column<*>? {
        if(references == null || references.isEmpty() || referee == null) {
            return referee
        } else {
            val reference = references.single { schemaTable ->
                schemaTable.table === referee.table
            }
            return reference.columns.single {
                it.name === referee.name
            }
        }

    }
}

