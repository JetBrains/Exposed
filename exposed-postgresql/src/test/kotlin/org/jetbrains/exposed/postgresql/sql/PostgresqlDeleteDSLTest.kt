package org.jetbrains.exposed.postgresql.sql

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PostgresqlDeleteDSLTest : BasePostgresTest() {

    @Test
    fun `delete with where`() {
        val anime = "Fairy Tail"
        val expectedRecordCount = 10

        assertThat(countByAnime(anime)).isZero()
        insert("Natsu Dragneel", anime, expectedRecordCount)
        assertThat(countByAnime(anime)).isEqualByComparingTo(expectedRecordCount.toLong())

        val (deletedCnt, interceptedStatements) = withTransaction {
            table.delete { where { table.anime.eq(anime) } }
        }
        assertThat(deletedCnt).isEqualByComparingTo(expectedRecordCount)

        val expectedSQL = normalizeSQL("""
            DELETE FROM $tableName WHERE $tableName.anime = '$anime'
        """.trimIndent())
        assertThat(interceptedStatements.exactlyOneStatement()).isEqualTo(expectedSQL)
    }

    @Test
    fun `delete where returning`() {
        val anime = "Fairy Tail 2014"
        val expectedRecordCount = 4
        assertThat(countByAnime(anime)).isZero()
        val insertedData = insert("Erza", anime, expectedRecordCount)
        assertThat(countByAnime(anime)).isEqualByComparingTo(expectedRecordCount.toLong())

        val (deletedReturned, interceptedStatements) = withTransaction {
            table.deleteReturning {
                where { table.anime.eq(anime) }
                returning()
            }.map { it.toEntity() }
        }
        assertThat(deletedReturned).hasSize(expectedRecordCount)
        assertThat(deletedReturned.map { it.id }).isEqualTo(insertedData.map { it.id })

        val expectedSQL = normalizeSQL("""
            DELETE FROM $tableName WHERE $tableName.anime = '$anime'
        """.trimIndent())
        assertThat(interceptedStatements.exactlyOneStatement()).isEqualTo(expectedSQL)
    }
}