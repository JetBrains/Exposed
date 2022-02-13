package org.jetbrains.exposed.postgresql.sql

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PostgresqlUpdateTest : BasePostgresTest() {

    @Test
    fun update() {
        val fullName = "Monkey D. Luffy"
        val insertedData = insert(fullName)
        val updatedName = "Hello $fullName"

        val (updatedCount, intercepted) = withTransaction {
            table.update {
                set { updateStatement ->
                    updateStatement[table.fullName] = updatedName
                }

                where { table.fullName.eq(insertedData.fullName) }
            }
        }

        assertThat(updatedCount).isOne()
        val expectedSQL = normalizeSQL("""
                UPDATE exposed_test 
                SET full_name='$updatedName' 
                WHERE exposed_test.full_name = '${fullName}'
            """.trimIndent())
        assertThat(intercepted.exactlyOneStatement()).isEqualTo(expectedSQL)
    }

    @Test
    fun `update returning works`() {
        val fullName = "Monkey D. Luffy 3"
        val insertedData = insert(fullName)
        val updatedFullName = "Hello $fullName"

        val (updatedReturning, intercepted) = withTransaction {
            table.updateReturning {
                set { updateStatement ->
                    updateStatement[table.fullName] = updatedFullName
                }

                where { table.fullName.eq(insertedData.fullName) }
                returning(table.slice(table.fullName))
            }.map { it[table.fullName] }
        }

        assertThat(updatedReturning).hasSize(1)
        assertThat(updatedReturning.first()).isEqualTo(updatedFullName)

        val expectedSQL = normalizeSQL("""
                UPDATE exposed_test 
                SET full_name='$updatedFullName' 
                WHERE exposed_test.full_name = '${insertedData.fullName}' 
                RETURNING exposed_test.full_name
            """.trimIndent())
        assertThat(intercepted.exactlyOneStatement()).isEqualTo(expectedSQL)
    }
}