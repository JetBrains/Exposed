package org.example.examples

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.util.*

const val MEMBER_NAME_LENGTH = 32
const val DEFAULT_BUDGET = 9999.0
const val MIN_BUDGET = 1000.0

class ArrayExamples {
    companion object {
        private const val TEAM_SIZE = 5
        private const val FIRST_MEMBER_INDEX = 1
        private const val SLICE_START = 1
        private const val SLICE_END_SMALL = 3
        private const val SLICE_END_LARGE = 4
    }

    // Simple array table definition
    object SimpleArrays : Table("teams") {
        val memberIds = array<UUID>("member_ids")
        val memberNames = array<String>("member_names")
        val budgets = array<Double>("budgets")
    }

    // Advanced array table definition
    object AdvancedArrays : Table("teams") {
        val memberNames = array<String>("member_names", VarCharColumnType(colLength = MEMBER_NAME_LENGTH))
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
            VarCharColumnType(colLength = MEMBER_NAME_LENGTH),
            dimensions = 3
        )
    }

    // Basic array usage example
    fun basicUsage() {
        transaction {
            SchemaUtils.create(SimpleArrays)

            SimpleArrays.insert {
                it[memberIds] = List(TEAM_SIZE) { UUID.randomUUID() }
                it[memberNames] = List(TEAM_SIZE) { i -> "Member ${('A' + i)}" }
                it[budgets] = listOf(DEFAULT_BUDGET)
            }
        }
    }

    // Array indexing example
    fun arrayIndexing() {
        transaction {
            val firstMember = SimpleArrays.memberIds[FIRST_MEMBER_INDEX]
            SimpleArrays
                .select(firstMember)
                .where { SimpleArrays.budgets[FIRST_MEMBER_INDEX] greater MIN_BUDGET }
        }
    }

    // Array slicing example
    fun arraySlicing() {
        transaction {
            SimpleArrays.select(SimpleArrays.memberNames.slice(SLICE_START, SLICE_END_SMALL))
        }
    }

    // Array operators example
    fun arrayOperators() {
        transaction {
            SimpleArrays
                .selectAll()
                .where { SimpleArrays.budgets[FIRST_MEMBER_INDEX] lessEq allFrom(SimpleArrays.budgets) }

            SimpleArrays
                .selectAll()
                .where { stringParam("Member A") eq anyFrom(SimpleArrays.memberNames.slice(SLICE_START, SLICE_END_LARGE)) }
        }
    }
}
