package org.example.examples

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.*

class ArrayExamples {
    // Simple array table definition
    object SimpleArrays : Table("teams") {
        val memberIds = array<UUID>("member_ids")
        val memberNames = array<String>("member_names")
        val budgets = array<Double>("budgets")
    }

    // Advanced array table definition
    object AdvancedArrays : Table("teams") {
        val memberNames = array<String>("member_names", VarCharColumnType(colLength = 32))
        val deadlines = array<LocalDate>("deadlines", KotlinLocalDateColumnType()).nullable()
        val expenses = array<Double?>("expenses", DoubleColumnType()).default(emptyList())
    }

    // Multidimensional array table definition
    object MultiDimArrays : Table("teams") {
        val nestedMemberIds = array<UUID, List<List<UUID>>>(
            "nested_member_ids", dimensions = 2
        )
        val hierarchicalMemberNames = array<String, List<List<List<String>>>>(
            "hierarchical_member_names",
            VarCharColumnType(colLength = 32),
            dimensions = 3
        )
    }

    // Basic array usage example
    fun basicUsage() {
        transaction {
            SchemaUtils.create(SimpleArrays)

            SimpleArrays.insert {
                it[memberIds] = List(5) { UUID.randomUUID() }
                it[memberNames] = List(5) { i -> "Member ${('A' + i)}" }
                it[budgets] = listOf(9999.0)
            }
        }
    }

    // Array indexing example
    fun arrayIndexing() {
        transaction {
            val firstMember = SimpleArrays.memberIds[1]
            SimpleArrays
                .select(firstMember)
                .where { SimpleArrays.budgets[1] greater 1000.0 }
        }
    }

    // Array slicing example
    fun arraySlicing() {
        transaction {
            SimpleArrays.select(SimpleArrays.memberNames.slice(1, 3))
        }
    }

    // Array operators example
    fun arrayOperators() {
        transaction {
            SimpleArrays
                .selectAll()
                .where { SimpleArrays.budgets[1] lessEq allFrom(SimpleArrays.budgets) }

            SimpleArrays
                .selectAll()
                .where { stringParam("Member A") eq anyFrom(SimpleArrays.memberNames.slice(1, 4)) }
        }
    }
}
