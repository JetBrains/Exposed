package org.jetbrains.exposed.sql.statements.api

import kotlinx.coroutines.flow.Flow

/**
 * Base class for wrapping data generated by executing a statement that queries the database.
 * */
// TODO: write KDocs, check naming
@Suppress("ForbiddenComment", "AnnotationSpacing")
interface ResultApi : AutoCloseable {
    fun <T> mapRows(block: (row: RowApi) -> T?): Flow<T?>
}

/**
 * Interface representing access to data rows by index or column name. This API allows retrieving
 * objects from a row, either by the zero-based index of the column or by its name, with optional
 * type conversion for strongly-typed results.
 */
interface RowApi {
    fun getObject(index: Int): Any?
    fun getObject(name: String): Any?

    fun <T> getObject(index: Int, type: Class<T>): T?
    fun <T> getObject(name: String, type: Class<T>): T?
}
