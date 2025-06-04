@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.domain.project

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.samples.r2dbc.domain.BaseRepository
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll

interface ProjectRepository : BaseRepository {
    suspend fun save(project: Project): ProjectId

    suspend fun findAll(): List<Project>

    suspend fun findById(id: ProjectId): Project?

    suspend fun delete(id: ProjectId): Boolean
}

class DSLProjectRepository : ProjectRepository {
    override suspend fun save(project: Project): ProjectId = dbQuery {
        val id = Projects.insert {
            it[Projects.name] = project.name
            it[Projects.code] = project.code
        } get Projects.id

        ProjectId(id)
    }

    override suspend fun findAll(): List<Project> = dbQuery {
        Projects
            .selectAll()
            .map(::rowToProject)
            .toList()
    }

    override suspend fun findById(id: ProjectId): Project? = dbQuery {
        Projects
            .selectAll()
            .where { Projects.id eq id.value }
            .singleOrNull()
            ?.let(::rowToProject)
    }

    override suspend fun delete(id: ProjectId): Boolean = dbQuery {
        val rowsDeleted = Projects.deleteWhere { Projects.id eq id.value }

        rowsDeleted == 1
    }
}
