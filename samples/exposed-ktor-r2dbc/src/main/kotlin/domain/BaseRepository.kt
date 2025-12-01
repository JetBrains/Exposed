@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.samples.r2dbc.domain.issue.Issues
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

interface BaseRepository {
    suspend fun <T> dbQuery(
        block: suspend R2dbcTransaction.() -> T
    ): T = withContext(Dispatchers.IO) {
        suspendTransaction {
            block()
        }
    }

    fun issueProjectMatches(id: Int): Op<Boolean> = Issues.projectId eq id

    fun issueKeysMatch(projectId: Int, number: Long): Op<Boolean> {
        return Issues.projectId eq projectId and (Issues.number eq number)
    }
}
