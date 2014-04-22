package kotlin.dao

import kotlin.sql.PKColumn

open class HistoryTable (masterColumn: PKColumn<EntityID>,name: String = "") : IdTable(name) {
    // reference to the master table of instances
    val master_id = entityId("${masterColumn.table.tableName}_id", masterColumn.table as IdTable) references masterColumn

    // start of the period when the row is active
    val start = date("start")

    // end of the period when the row is active. Null means still valid
    val end = date("end").nullable()
}
