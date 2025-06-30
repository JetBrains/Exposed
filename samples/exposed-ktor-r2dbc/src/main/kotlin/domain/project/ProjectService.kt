@file:Suppress("InvalidPackageDeclaration")

package org.jetbrains.exposed.samples.r2dbc.domain.project

class ProjectService(
    private val projectRepository: ProjectRepository
) {
    suspend fun createProject(project: Project): Project {
        val id = projectRepository.save(project)
        return project.copy(id = id)
    }

    suspend fun getProjects(): List<Project> {
        return projectRepository.findAll()
    }

    suspend fun getProject(id: Int): Project? {
        return projectRepository.findById(ProjectId(id))
    }

    suspend fun removeProject(id: Int): Boolean {
        return projectRepository.delete(ProjectId(id))
    }
}
