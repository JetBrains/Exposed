package org.jetbrains.exposed.v1.tests.shared.entities

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityReferrersTests : DatabaseTestsBase() {

    object AlertItemTable : IntIdTable("alert_item") {
        val isAlarm = bool("is_alarm").default(true)
    }

    class AlertItemEntity(id: EntityID<Int>) : IntEntity(id) {
        companion object : IntEntityClass<AlertItemEntity>(AlertItemTable)

        val bids by ItemBidEntity.referrersOn(ItemBidTable.alertItemId, cache = true)
    }

    object ItemBidTable : LongIdTable("item_bid") {
        val alertItemId = integer("alert_item_id").references(AlertItemTable.id, onDelete = ReferenceOption.CASCADE)
    }

    class ItemBidEntity(id: EntityID<Long>) : LongEntity(id) {
        companion object : LongEntityClass<ItemBidEntity>(ItemBidTable)
    }

    @Test
    fun testCacheIsUsedWithReference() {
        withTables(AlertItemTable, ItemBidTable) {
            repeat(3) {
                val itemId = AlertItemTable.insertAndGetId {
                    it[isAlarm] = true
                }
                repeat(5) {
                    ItemBidTable.insertAndGetId {
                        it[alertItemId] = itemId.value
                    }
                }
            }

            val counter = executionsCounter()

            AlertItemEntity
                .find { AlertItemTable.isAlarm eq true }
                .with(AlertItemEntity::bids)

            assertEquals(2, counter.count, "'find()' must execute exactly 2 statements. One to fetch items, another one to fetch all the bids")
        }
    }
}
