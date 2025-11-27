package org.jetbrains.exposed.v1.tests.shared.entities

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.tests.DatabaseTestsBase
import org.jetbrains.exposed.v1.tests.shared.assertEquals
import org.junit.jupiter.api.Test

// TODO
class WarmUpLinkedReferencesTests : DatabaseTestsBase() {

    object Box : IntIdTable() {
        val value = integer("value")
    }

    class EBox(id: EntityID<Int>) : IntEntity(id) {
        var value by Box.value

        companion object : IntEntityClass<EBox>(Box)
    }

    object BoxItem : IntIdTable() {
        val box = reference("box", Box)

        val value = integer("value")
    }

    class EBoxItem(id: EntityID<Int>) : IntEntity(id) {
        var value by BoxItem.value
        var box by EBox referencedOn BoxItem.box

        companion object : IntEntityClass<EBoxItem>(BoxItem)
    }

    @Test
    fun warmUpLinkedReferencesShouldNotReturnAllTheValueFromCache() {
        withTables(Box, BoxItem) {
            val boxEntities = (0..4).map {
                EBox.new {
                    value = it
                }
            }

            boxEntities.forEach {
                EBoxItem.new {
                    box = it
                    value = it.id.value
                }
            }

            val ids = boxEntities.map { it.id }

            // Warm up all the entities to fill the cache
            EBox.warmUpLinkedReferences(ids, BoxItem)

            val warmedUp = EBox.warmUpLinkedReferences(ids.slice(0..2), BoxItem)
            assertEquals(3, warmedUp.size)
        }
    }
}
