package org.jetbrains.exposed.sql.tests.shared.ddl

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.tests.DatabaseTestsBase
import org.jetbrains.exposed.sql.tests.currentDialectTest
import org.jetbrains.exposed.sql.tests.shared.assertEquals
import org.junit.Test

class CreateSequenceTests : DatabaseTestsBase() {
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
    fun SequenceNextValTest() {
        withTables(Developer) {
            if (currentDialectTest.supportsCreateSequence) {
                try {
                    SchemaUtils.createSequence(myseq)

                    var developerId = Developer.insert {
                        it[id] = myseq.nextVal()
                        it[name] = "Hichem"
                    } get Developer.id

                    assertEquals(4, developerId)

                    developerId = Developer.insert {
                        it[id] = myseq.nextVal()
                        it[name] = "Andrey"
                    } get Developer.id

                    assertEquals(6, developerId)
                } finally {
                    SchemaUtils.dropSequence(myseq)
                }
            }
        }
    }

    object Developer : Table() {
        val id = integer("id")
        var name = varchar("name", 25)

        override val primaryKey = PrimaryKey(id, name)
    }
    val myseq = Sequence(name= "my_sequence",
                        startWith= 4,
                        incrementBy= 2,
                        minValue= 1,
                        maxValue= 10,
                        cycle= true,
                        cache=20)
}
