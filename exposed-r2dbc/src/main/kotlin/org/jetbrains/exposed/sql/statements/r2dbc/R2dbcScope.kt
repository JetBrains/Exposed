package org.jetbrains.exposed.sql.statements.r2dbc

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Class responsible for managing the scope of the underlying R2DBC database connection object.
 */
class R2dbcScope(dispatcher: CoroutineDispatcher?) : CoroutineScope {
    override val coroutineContext: CoroutineContext = dispatcher
        ?.let { EmptyCoroutineContext + it }
        ?: EmptyCoroutineContext
}
