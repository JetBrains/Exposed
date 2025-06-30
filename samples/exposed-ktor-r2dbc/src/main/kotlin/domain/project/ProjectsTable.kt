@file:Suppress("InvalidPackageDeclaration", "MatchingDeclarationName", "MagicNumber")

package org.jetbrains.exposed.samples.r2dbc.domain.project

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.charLength

object Projects : Table("projects") {
    val id = integer("id").autoIncrement()

    val name = varchar("name", 32)
    val code = varchar("code", 7)
        .check { it.charLength().between(3, 7) }
        .uniqueIndex()

    override val primaryKey = PrimaryKey(id)
}

fun rowToProject(result: ResultRow): Project = Project(
    id = ProjectId(result[Projects.id]),
    name = result[Projects.name],
    code = result[Projects.code]
)
