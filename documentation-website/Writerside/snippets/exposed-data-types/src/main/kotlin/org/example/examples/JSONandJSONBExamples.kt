package org.example.examples

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.json.json
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.json.contains
import org.jetbrains.exposed.sql.json.exists
import org.jetbrains.exposed.sql.json.extract
import org.jetbrains.exposed.sql.lowerCase

/*
    Important: The `movie.delete` statement is referenced by line number in `JSON-and-JSONB.topic`.
    If you add, remove, or modify any lines prior to this one, ensure you update the corresponding
    line numbers in the `code-block` element of the referenced file.
*/

@Serializable
data class Project(val name: String, val language: String, val active: Boolean)

val format = Json { prettyPrint = true }

object TeamsTable : Table("team") {
    val groupId = varchar("group_id", 32)
    val project = json<Project>("project", format) // equivalent to json("project", format, Project.serializer())
}

// using Jackson

val mapper = jacksonObjectMapper()

object JacksonTeamsTable : Table("team") {
    val groupId = varchar("group_id", 32)
    val project = json("project", { mapper.writeValueAsString(it) }, { mapper.readValue<Project>(it) })
}

object TeamProjectsTable : Table("team_projects") {
    val memberIds = json<IntArray>("member_ids", Json.Default)
    val projects = json<Array<Project>>("projects", Json.Default)
    // equivalent to:
    // @OptIn(ExperimentalSerializationApi::class) json("projects", Json.Default, ArraySerializer(Project.serializer()))
}

class JSONandJSONBExamples {
    fun example() {
        val mainProject = Project("Main", "Java", true)
        TeamsTable.insert {
            it[groupId] = "A"
            it[project] = mainProject
        }
        TeamsTable.update({ TeamsTable.groupId eq "A" }) {
            it[project] = mainProject.copy(language = "Kotlin")
        }

        TeamsTable.selectAll().map { "Team ${it[TeamsTable.groupId]} -> ${it[TeamsTable.project]}" }.forEach { println(it) }
        // Team A -> Project(name=Main, language=Kotlin, active=true)
    }

    /*
        Generated SQL:

        SELECT JSON_UNQUOTE(JSON_EXTRACT(team.project, "$.name"))
        FROM team
        WHERE LOWER(JSON_UNQUOTE(JSON_EXTRACT(team.project, "$.language"))) = 'kotlin'
     */
    fun useExtract() {
        val projectName = TeamsTable.project.extract<String>(".name")
        val languageIsKotlin = TeamsTable.project.extract<String>(".language").lowerCase() eq "kotlin"
        TeamsTable.select(projectName).where { languageIsKotlin }.map { it[projectName] }
    }

    fun useExists() {
        val hasActiveStatus = TeamsTable.project.exists(".active")
        val activeProjects = TeamsTable.selectAll().where { hasActiveStatus }.count()
    }

    // Depending on the database, filter paths can be provided instead, as well as optional arguments
    // PostgreSQL example
    fun useExistsWithFilterPath() {
        val mainId = "Main"
        val hasMainProject = TeamsTable.project.exists(".name ? (@ == \$main)", optional = "{\"main\":\"$mainId\"}")
        val mainProjects = TeamsTable.selectAll().where { hasMainProject }.map { it[TeamsTable.groupId] }

    }

    fun useContains() {
        val usesKotlin = TeamsTable.project.contains("{\"language\":\"Kotlin\"}")
        val kotlinTeams = TeamsTable.selectAll().where { usesKotlin }.count()
        println(kotlinTeams)
    }

    // Depending on the database, an optional path can be provided too
    // MySQL example
    fun useContainsWithPath(){
        val usesKotlinWithPath = TeamsTable.project.contains("\"Kotlin\"", ".language")
        val kotlinTeams = TeamsTable.selectAll().where { usesKotlinWithPath }.count()
        println(kotlinTeams)
    }

    /*
     Generated SQL:

     INSERT INTO team_projects (member_ids, projects)
     VALUES ([1,2,3], [{"name":"A","language":"Kotlin","active":true},{"name":"B","language":"Java","active":true}])
     */
    fun insertJSONArrays() {
        TeamProjectsTable.insert {
            it[memberIds] = intArrayOf(1, 2, 3)
            it[projects] = arrayOf(
                Project("A", "Kotlin", true),
                Project("B", "Java", true)
            )
        }
    }
}
