package org.jetbrains.exposed.postgresql.sql

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Test

class PostgresqlInsertDSLTest : BasePostgresTest() {

    @Test
    fun `postgres insert works`() {
        val fullName = "Dracule Mihawk"
        assertThat(selectByFullName(fullName)).isNull()

        val (result, intercepted) = withTransaction {
            PostgresTestTable.insert {
                values { insertStatement ->
                    insertStatement[name] = fullName
                }
            }
        }

        assertThat(result).isOne()
        assertThat(intercepted.executedStatements).hasSize(1)
        val expectedSql = normalizeSQl("""
            INSERT INTO exposed_test (full_name) VALUES ('$fullName')
        """)
        assertThat(expectedSql).isEqualTo(intercepted.executedStatements.first())

        val insertedData = selectByFullName(fullName)
        assertThat(insertedData).isNotNull()
        assertThat(insertedData!!.id).isGreaterThan(0)
        assertThat(insertedData.fullName).isEqualTo(fullName)
    }

    @Test
    fun `postgres insert returning all works`() {
        val fullName = "Dracule Mihawk 1"
        assertThat(selectByFullName(fullName)).isNull()

        val (result, intercepted) = withTransaction {
            PostgresTestTable.insertReturning {
                values { insertStatement ->
                    insertStatement[name] = fullName
                }
            }
        }

        assertThat(intercepted.executedStatements).hasSize(1)
        val executedStatement = intercepted.executedStatements.first()
        val expectedStatement = normalizeSQl("""
            INSERT INTO exposed_test (full_name) VALUES ('$fullName') 
            RETURNING exposed_test.id, exposed_test.full_name
        """)
        assertThat(executedStatement).isEqualTo(expectedStatement)

        val returnedData = result.toExposedPostgresTableData()
        val expectedData = selectByFullName(fullName)
        assertThat(expectedData).isNotNull
        assertThat(returnedData.id).isEqualTo(expectedData!!.id)
        assertThat(returnedData.fullName).isEqualTo(expectedData.fullName)
    }

    @Test
    fun `postgres insert returning full_name works`() {
        val fullName = "Dracule Mihawk 2"
        assertThat(selectByFullName(fullName)).isNull()

        val (result, intercepted) = withTransaction {
            PostgresTestTable.insertReturning {
                values { insertStatement ->
                    insertStatement[name] = fullName
                }

                returning(returning = table.slice(table.name))
            }
        }

        assertThat(intercepted.executedStatements).hasSize(1)
        val executedStatement = intercepted.executedStatements.first()
        val expectedStatement = normalizeSQl("""
            INSERT INTO exposed_test (full_name) VALUES ('$fullName') 
            RETURNING exposed_test.full_name
        """)
        assertThat(executedStatement).isEqualTo(expectedStatement)

        val returnedFullName = result[table.name]
        val expectedData = selectByFullName(fullName)
        assertThat(expectedData).isNotNull
        assertThat(returnedFullName).isEqualTo(expectedData!!.fullName)
    }

    @Test
    fun `insert on conflict do nothing with conflict target column`() {
        val fullName = "Dracule Mihawk 3"
        assertThat(selectByFullName(fullName)).isNull()

        //now insert data for future conflict
        val (insertCount, _) = withTransaction {
            PostgresTestTable.insert {
                values { insertStatement ->
                    insertStatement[name] = fullName
                }
            }
        }

        assertThat(insertCount).isOne()
        assertThat(selectByFullName(fullName)).isNotNull

        val (onCflInsertCount, intercepted) = withTransaction {
            PostgresTestTable.insert {
                values { insertStatement ->
                    insertStatement[name] = fullName
                }

                onConflictDoNothing(table.name)
            }
        }

        val executedStatement = intercepted.executedStatements.first()
        val expectedStatement = normalizeSQl("""
            INSERT INTO exposed_test (full_name) VALUES ('$fullName') 
            ON CONFLICT (full_name) DO NOTHING
        """)
        assertThat(executedStatement).isEqualTo(expectedStatement)
        assertThat(onCflInsertCount).isZero()
        assertThat(countByFullName(fullName)).isOne()
    }

    @Test
    fun `insert on conflict do nothing without conflict target`() {
        val fullName = "Dracule Mihawk 4"
        assertThat(selectByFullName(fullName)).isNull()

        //now insert data for future conflict
        val (insertCount, _) = withTransaction {
            PostgresTestTable.insert {
                values { insertStatement ->
                    insertStatement[name] = fullName
                }
            }
        }

        assertThat(insertCount).isOne()
        assertThat(selectByFullName(fullName)).isNotNull

        val (onCflInsertCount, intercepted) = withTransaction {
            PostgresTestTable.insert {
                values { insertStatement ->
                    insertStatement[name] = fullName
                }

                onConflictDoNothing()
            }
        }

        val executedStatement = intercepted.executedStatements.first()
        val expectedStatement = normalizeSQl("""
            INSERT INTO exposed_test (full_name) VALUES ('$fullName') 
            ON CONFLICT DO NOTHING
        """)
        assertThat(executedStatement).isEqualTo(expectedStatement)
        assertThat(onCflInsertCount).isZero()
        assertThat(countByFullName(fullName)).isOne()
    }

    @Test
    fun `insert on conflict do nothing conflict target is constraint`() {
        val fullName = "Dracule Mihawk 5"
        assertThat(selectByFullName(fullName)).isNull()

        //now insert data for future conflict
        val (insertCount, _) = withTransaction {
            PostgresTestTable.insert {
                values { insertStatement ->
                    insertStatement[name] = fullName
                }
            }
        }

        assertThat(insertCount).isOne()
        assertThat(selectByFullName(fullName)).isNotNull

        val (onCflInsertCount, intercepted) = withTransaction {
            PostgresTestTable.insert {
                values { insertStatement ->
                    insertStatement[name] = fullName
                }

                onConflictDoNothingConstraint("unique_full_name")
            }
        }

        val executedStatement = intercepted.executedStatements.first()
        val expectedStatement = normalizeSQl("""
            INSERT INTO exposed_test (full_name) VALUES ('$fullName') 
            ON CONFLICT ON CONSTRAINT unique_full_name DO NOTHING
        """)
        assertThat(executedStatement).isEqualTo(expectedStatement)
        assertThat(onCflInsertCount).isZero()
        assertThat(countByFullName(fullName)).isOne()
    }

    private fun selectByFullName(fullName: String): ExposedPostgresTableData? {
        return transaction {
            table.select { table.name.eq(fullName) }.singleOrNull()?.toExposedPostgresTableData()
        }
    }

    private fun countByFullName(fullName: String): Long {
        return transaction { table.select { table.name.eq(fullName) }.count() }
    }

    companion object {
        private val table = PostgresTestTable
    }
}