package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class SequencesTests : DatabaseTestsBase() {
    @Test
    fun createSequenceStatementTest() {
        withDb {
            if (currentDialectTest.supportsCreateSequence) {
                assertEquals(
                    "CREATE SEQUENCE " + addIfNotExistsIfSupported() + "${myseq.identifier} " +
                            "START WITH ${myseq.startWith} " +
                            "INCREMENT BY ${myseq.incrementBy} " +
                            "MINVALUE ${myseq.minValue} " +
                            "MAXVALUE ${myseq.maxValue} " +
                            "CYCLE " +
                            "CACHE ${myseq.cache}",
                    myseq.ddl
                )
            }
        }
    }

    @Test
    fun `test insert with sequences`() {
        withTables(Developer) {
            if (currentDialectTest.supportsSequenceAsGeneratedKeys) {
                try {
                    SchemaUtils.createSequence(myseq)

                    var developerId = Developer.insert {
                        it[id] = myseq.nextVal()
                        it[name] = "Hichem"
                    } get Developer.id

                    assertEquals(myseq.startWith, developerId)

                    developerId = Developer.insert {
                        it[id] = myseq.nextVal()
                        it[name] = "Andrey"
                    } get Developer.id

                    assertEquals(myseq.startWith!! + myseq.incrementBy!!, developerId)
                } finally {
                    SchemaUtils.dropSequence(myseq)
                }
            }
        }
    }

    @Test
    fun `test select with nextVal`() {
        withTables(Developer) {
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.createSequence(myseq)
                    val nextVal = myseq.nextVal()
                    Developer.insert {
                        it[id] = nextVal
                        it[name] = "Hichem"
                    }

                    val firstValue = Developer.slice(nextVal).selectAll().single()[nextVal]
                    val secondValue = Developer.slice(nextVal).selectAll().single()[nextVal]

                    val expFirstValue = myseq.startWith!! + myseq.incrementBy!!
                    assertEquals(expFirstValue, firstValue)

                    val expSecondValue = expFirstValue + myseq.incrementBy!!
                    assertEquals(expSecondValue, secondValue)

                } finally {
                    SchemaUtils.dropSequence(myseq)
                }
            }
        }
    }

    @Test
    fun `test select with nextValLong`() {
        withTables(DeveloperLong) {
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.createSequence(myseq)
                    val nextVal = myseq.nextValLong()
                    DeveloperLong.insert {
                        it[id] = nextVal
                        it[name] = "Hichem"
                    }

                    val firstValue = DeveloperLong.slice(nextVal).selectAll().single()[nextVal]
                    val secondValue = DeveloperLong.slice(nextVal).selectAll().single()[nextVal]

                    val expFirstValue = myseq.startWith!! + myseq.incrementBy!!.toLong()
                    assertEquals(expFirstValue, firstValue)

                    val expSecondValue = expFirstValue + myseq.incrementBy!!.toLong()
                    assertEquals(expSecondValue, secondValue)

                } finally {
                    SchemaUtils.dropSequence(myseq)
                }
            }
        }
    }

    private object Developer : Table() {
        val id = integer("id")
        var name = varchar("name", 25)

        override val primaryKey = PrimaryKey(id, name)
    }

    private object DeveloperLong : Table() {
        val id = long("id")
        var name = varchar("name", 25)

        override val primaryKey = PrimaryKey(id, name)
    }

    private val myseq = Sequence(
        name = "my_sequence",
        startWith = 4,
        incrementBy = 2,
        minValue = 1,
        maxValue = 10,
        cycle = true,
        cache = 20
    )
}
