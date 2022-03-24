package org.jetbrains.exposed.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.exposed.gradle.tasks.DBTest
import org.jetbrains.exposed.gradle.tasks.DBTestWithDockerCompose
import org.jetbrains.exposed.gradle.tasks.DBTestWithDockerCompose.Parameters

class DBTestingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(JavaPlugin::class.java)
        project.pluginManager.apply("com.avast.gradle.docker-compose")

        with(project.tasks) {
            val h2_v1 = register<DBTest>("h2_v1Test", "H2,H2_MYSQL") {
                testRuntimeOnly("com.h2database", "h2", Versions.h2)
            }
            val h2_v2 = register<DBTest>("h2_v2Test", "H2,H2_MYSQL") {
                testRuntimeOnly("com.h2database", "h2", Versions.h2_v2)
            }
            val h2 = register<Test>("h2Test") {
                group = "verification"
                delegatedTo(h2_v1, h2_v2)
            }

            val sqlite = register<DBTest>("sqliteTest", "SQLITE") {
                testRuntimeOnly("org.xerial", "sqlite-jdbc", Versions.sqlLite3)
            }

            val mysql51 = register<DBTestWithDockerCompose>("mysql51Test", Parameters("MYSQL", 3306)) {
                testRuntimeOnly("mysql", "mysql-connector-java", Versions.mysql51)
            }
            val mysql80 = register<DBTestWithDockerCompose>("mysql80Test", Parameters("MYSQL", 3306, "mysql8")) {
                testRuntimeOnly("mysql", "mysql-connector-java", Versions.mysql80)
            }
            val mysql = register<Test>("mysqlTest") {
                group = "verification"
                delegatedTo(mysql51, mysql80)
            }

            val postgres = register<DBTest>("postgresTest", "POSTGRESQL") {
                testRuntimeOnly("org.postgresql", "postgresql", Versions.postgre)
            }
            val postgresNG = register<DBTest>("postgresNGTest", "POSTGRESQLNG") {
                testRuntimeOnly("org.postgresql", "postgresql", Versions.postgre)
                testRuntimeOnly("com.impossibl.pgjdbc-ng", "pgjdbc-ng", Versions.postgreNG)
            }

            val oracle = register<DBTestWithDockerCompose>("oracleTest", Parameters("ORACLE", 1521)) {
                testRuntimeOnly("com.oracle.database.jdbc", "ojdbc8", Versions.oracle12)
            }

            val sqlServer = register<DBTestWithDockerCompose>("sqlServerTest", Parameters("SQLSERVER", 1433)) {
                testRuntimeOnly("com.microsoft.sqlserver", "mssql-jdbc", Versions.sqlserver)
            }

            val mariadb = register<DBTestWithDockerCompose>("mariadbTest", Parameters("MARIADB", 3306)) {
                testRuntimeOnly("org.mariadb.jdbc", "mariadb-java-client", Versions.mariaDB)
            }

            val db2 = register<DBTest>("db2Test", "db2") {
                testRuntimeOnly("com.ibm.db2", "jcc", Versions.db2)
            }

            named<Test>("test") {
                delegatedTo(
                    h2,
                    sqlite,
                    mysql51,
                    postgres,
                    postgresNG,
                    db2
                )
            }
        }
    }
}

/**
 * Defines and configure a new task, which will be created when it is required passing the given arguments to the [javax.inject.Inject]-annotated constructor.
 *
 * @see [TaskContainer.register]
 */
inline fun <reified T : Task> TaskContainer.register(name: String, vararg arguments: Any, noinline configuration: T.() -> Unit): TaskProvider<T> =
    register(name, T::class.java, *arguments).apply { configure { configuration() } }

fun Test.delegatedTo(vararg tasks: TaskProvider<out AbstractTestTask>): Test {
    // don't run tests directly, delegate to other tasks
    filter {
        setExcludePatterns("*")
        isFailOnNoMatchingTests = false
    }
    finalizedBy(tasks)
    //Pass --tests CLI option value into delegates
    doFirst {
        val testsFilter = (filter as DefaultTestFilter).commandLineIncludePatterns.toList()
        tasks.forEach {
            it.configure { setTestNameIncludePatterns(testsFilter) }
        }
    }
    return this
}
