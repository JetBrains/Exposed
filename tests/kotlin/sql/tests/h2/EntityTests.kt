package kotlin.sql.tests.h2

import org.junit.Test
import kotlin.dao.*
import kotlin.sql.*
import kotlin.sql.tests.h2.DatabaseTestsBase
import kotlin.test.assertEquals

object EntityTestsData {
    object XTable: IdTable() {
        val b1 = bool("b1").default(true)
        val b2 = bool("b2").default(false)
    }

    class XEntity(id: EntityID): Entity(id) {
        var b1 by XTable.b1
        var b2 by XTable.b2

        companion object : EntityClass<XEntity>(XTable) {
        }
    }
}

public class EntityTests: DatabaseTestsBase() {
    Test fun testDefaults01() {
        withTables(EntityTestsData.XTable) {
            val x = EntityTestsData.XEntity.new {  }
            assertEquals (x.b1, true, "b1 mismatched")
            assertEquals (x.b2, false, "b2 mismatched")
        }
    }
}