package org.jetbrains.exposed.sql.tests.shared


import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.IntIdTableInterface
import org.jetbrains.exposed.dao.id.NextIntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.util.*


interface SoftDeleteTable {
    val deleted: Column<Boolean>
}


class SoftDeleteTableImpl(val table: ITable): SoftDeleteTable, ITable by table {
    override var deleted: Column<Boolean> = bool("is_deleted")
}

interface TimeStampTable {
    val timestamp: Column<String>
}

class TimeStampTableImpl(val table: ITable): TimeStampTable, ITable by table {
    override var timestamp: Column<String> = varchar("timestamp", 50)
}

// create a table that join every interface with their respective delegation
val baseTable = IntIdTable()
// create a proxy that adds columns to that baseTable
object BookTable2: IntIdTableInterface by baseTable,
                        SoftDeleteTable by SoftDeleteTableImpl(baseTable),
                        TimeStampTable by TimeStampTableImpl(baseTable) {
    var name  = varchar("name", 100)
}

fun main() {
    Database.connect("jdbc:h2:mem:test", driver = "org.h2.Driver", user = "root", password = "")

    transaction {
        addLogger(StdOutSqlLogger)

        SchemaUtils.create (BookTable2)

        val bookTableId = BookTable2.insert {
            it[name] = "St. Petersburg"
            it[deleted] = false
            it[timestamp] = Date().toString()
        } get BookTable2.id

        SchemaUtils.drop (BookTable2)
    }
}

class SamplesSQL {
    @Test
    fun ensureSamplesDoesntCrash(){
        main()
    }
}
