package org.jetbrains.exposed.dao.r2dbc.tests.shared.dml

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.IntR2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.v1.core.ColumnTransformer
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.tests.R2dbcDatabaseTestsBase
import org.jetbrains.exposed.v1.r2dbc.tests.shared.assertEquals
import kotlin.test.Test

class R2dbcColumnWithTransformTest : R2dbcDatabaseTestsBase() {
    data class TransformDataHolder(val value: Int)

    class DataHolderTransformer : ColumnTransformer<Int, TransformDataHolder> {
        override fun unwrap(value: TransformDataHolder): Int = value.value
        override fun wrap(value: Int): TransformDataHolder = TransformDataHolder(value)
    }

    object TransformTable : IntIdTable("transform_table") {
        val simple = integer("simple")
            .default(1)
            .transform(DataHolderTransformer())
        val chained = varchar("chained", length = 128)
            .transform(wrap = { it.toInt() }, unwrap = { it.toString() })
            .transform(DataHolderTransformer())
            .default(TransformDataHolder(2))
    }

    class TransformEntity(id: EntityID<Int>) : IntR2dbcEntity(id) {
        var simple by TransformTable.simple
        var chained by TransformTable.chained

        companion object : IntR2dbcEntityClass<TransformEntity>(TransformTable)
    }

    @Test
    fun testTransformedValuesWithDAO() {
        withTables(TransformTable) {
            val entity = TransformEntity.new {
                this.simple = TransformDataHolder(120)
                this.chained = TransformDataHolder(240)
            }

            val row = TransformTable.selectAll().first()
            assertEquals(TransformDataHolder(120), row[TransformTable.simple])
            assertEquals(TransformDataHolder(240), row[TransformTable.chained])

            assertEquals(TransformDataHolder(120), entity.simple)
            assertEquals(TransformDataHolder(240), entity.chained)
        }
    }

    @Test
    fun testEntityWithDefaultValue() {
        withTables(TransformTable) {
            val entity = TransformEntity.new {}

            assertEquals(TransformDataHolder(1), entity.simple)
            assertEquals(TransformDataHolder(2), entity.chained)

            val entry = TransformTable.selectAll().first()

            assertEquals(1, entry[TransformTable.simple].value)
            assertEquals(2, entry[TransformTable.chained].value)
        }
    }

    @Test
    fun testWrapRowWithAliases() {
        withTables(TransformTable) {
            TransformEntity.new {
                simple = TransformDataHolder(10)
            }
            entityCache.clear()

            val tableAlias = TransformTable.alias("table_alias")
            val e2 = tableAlias.selectAll().map { TransformEntity.wrapRow(it, tableAlias) }.first()
            assertEquals(10, e2.simple.value)
            entityCache.clear()

            val queryAlias = TransformTable.selectAll().alias("query_alias")
            val e3 = queryAlias.selectAll().map { TransformEntity.wrapRow(it, queryAlias) }.first()
            assertEquals(10, e3.simple.value)
        }
    }
}
