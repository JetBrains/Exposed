package org.jetbrains.exposed.v1.core

internal class TableDepthGraph(val tables: Iterable<Table>) {
    val graph = fetchAllTables().let { tables ->
        if (tables.isEmpty()) {
            emptyMap()
        } else {
            tables.associateWith { t ->
                t.foreignKeys.map { it.targetTable }
            }
        }
    }

    private fun fetchAllTables(): HashSet<Table> {
        val result = HashSet<Table>()
        fun parseTable(table: Table) {
            if (result.add(table)) {
                table.foreignKeys.map { it.targetTable }.forEach(::parseTable)
            }
        }
        tables.forEach(::parseTable)
        return result
    }

    fun sorted(): List<Table> {
        if (!tables.iterator().hasNext()) return emptyList()
        val visited = mutableSetOf<Table>()
        val result = arrayListOf<Table>()
        fun traverse(table: Table) {
            if (table !in visited) {
                visited += table
                graph.getValue(table).forEach { t ->
                    if (t !in visited) {
                        traverse(t)
                    }
                }
                result += table
            }
        }
        tables.forEach(::traverse)
        return result
    }

    fun hasCycle(): Boolean {
        if (!tables.iterator().hasNext()) return false
        val visited = mutableSetOf<Table>()
        val recursion = mutableSetOf<Table>()
        val sortedTables = sorted()
        fun traverse(table: Table): Boolean {
            if (table in recursion) return true
            if (table in visited) return false
            recursion += table
            visited += table
            return if (graph[table]!!.any { traverse(it) }) {
                true
            } else {
                recursion -= table
                false
            }
        }
        return sortedTables.any { traverse(it) }
    }
}
