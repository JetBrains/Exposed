package org.jetbrains.exposed.v1.spring.registryfixture.failing

import org.jetbrains.exposed.v1.core.dao.id.LongIdTable

/**
 * A table object whose initializer fails. It is outside the scanned test package `org.jetbrains.exposed.v1.spring.data`,
 * so only the registry test that scans this package sees it.
 */
object FailingTables : LongIdTable("registry_failing") {
    init {
        error("broken table")
    }
}
