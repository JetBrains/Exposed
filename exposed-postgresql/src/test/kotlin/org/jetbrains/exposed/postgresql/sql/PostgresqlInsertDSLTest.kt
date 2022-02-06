package org.jetbrains.exposed.postgresql.sql

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PostgresqlInsertDSLTest : BasePostgresTest() {

    @Test
    fun `postgres insert works`() {
        val (result, intercepted) = withTransaction {
            PostgresTestTable.insert {
                values { insertStatement ->
                    insertStatement[name] = "Dracule Mihawk"
                }
            }
        }

        assertThat(result).isOne()
        assertThat(intercepted.)
    }
}