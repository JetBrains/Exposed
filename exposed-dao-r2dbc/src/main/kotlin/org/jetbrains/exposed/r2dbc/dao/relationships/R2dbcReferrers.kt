package org.jetbrains.exposed.r2dbc.dao.relationships

import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntity
import org.jetbrains.exposed.r2dbc.dao.R2dbcEntityClass
import org.jetbrains.exposed.r2dbc.dao.entityCache
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.transactions.TransactionManager
import kotlin.reflect.KProperty

class R2dbcReferrers<ParentID : Any, in Parent : R2dbcEntity<ParentID>, ChildID : Any, out Child : R2dbcEntity<ChildID>, REF>(
    val reference: Column<REF>,
    val factory: R2dbcEntityClass<ChildID, Child>,
    val cache: Boolean
) {
    init {
        // Validate that reference column points to the parent entity's table
        val referee = reference.referee ?: error("Column $reference is not a reference")

        // Validate that reference column is on the child entity's table
        if (factory.table != reference.table) {
            error("Column $reference and factory ${factory.table.tableName} point to different tables")
        }
    }

    @Suppress("NestedBlockDepth", "ForbiddenComment")
    operator fun getValue(thisRef: Parent, property: KProperty<*>): suspend () -> List<Child> {
        // Return a suspend lambda that will load the referrers when invoked
        return {
            // Check if entity ID is available
            if (thisRef.id._value == null) {
                // TODO should it be error?
                emptyList()
            } else {
                val transaction = TransactionManager.currentOrNull()

                // Out-of-transaction access: return cached data
                if (transaction == null) {
                    if (thisRef.hasInReferenceCache(reference)) {
                        val cached = thisRef.getReferenceFromCache<Any?>(reference)
                        when (cached) {
                            is List<*> -> cached as List<Child>
                            null -> emptyList()
                            else -> error("Cached referrer has unexpected type: ${cached::class}")
                        }
                    } else {
                        error("No transaction in context, and referrers not in entity cache for $reference")
                    }
                } else {
                    // Get the parent entity's ID value to use in query
                    val referee = reference.referee!!

                    @Suppress("UNCHECKED_CAST")
                    val refValue = with(thisRef) {
                        referee.lookup()
                    } as REF

                    // Build the query for child entities
                    val query: suspend () -> List<Child> = {
                        val resultRows = factory.find { reference eq refValue }.toList()
                        resultRows.map { factory.wrapRow(it) }
                    }

                    // Execute query with caching if enabled
                    val result = if (cache) {
                        @Suppress("UNCHECKED_CAST")
                        transaction.entityCache.getOrPutReferrers<ParentID>(reference, thisRef.id, query) as List<Child>
                    } else {
                        query()
                    }

                    // Store in entity's reference cache for out-of-transaction access
                    thisRef.storeReferenceInCache(reference, result)

                    result
                }
            }
        }
    }
}
