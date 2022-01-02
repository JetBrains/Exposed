package org.jetbrains.exposed.gradle.tasks

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByName
import javax.inject.Inject

open class DBTest @Inject constructor(@get:Input val dialect: String) : Test() {
    init {
        group = "verification"
        val projectSourceSets = project.extensions.getByName<SourceSetContainer>("sourceSets")
        val projectTestSourceSet = projectSourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)
        val projectMainSourceSet = projectSourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        testClassesDirs = projectTestSourceSet.output.classesDirs
        classpath = projectMainSourceSet.output + projectTestSourceSet.output + projectTestSourceSet.runtimeClasspath
    }

    @TaskAction
    override fun executeTests() {
        withSystemProperties("exposed.test.dialects" to dialect) {
            super.executeTests()
        }
    }

    protected fun withSystemProperties(vararg sysProp: Pair<String, Any>, action: DBTest.() -> Unit) {
        val prevValues = sysProp.associate { (name, _) -> name to systemProperties[name] }
        sysProp.forEach { (name, value) -> systemProperty(name, value) }
        action()
        prevValues.forEach { (name, value) -> systemProperty(name, value ?: "") }
    }

    fun testRuntimeOnly(group: String, name: String, version: String) {
        classpath += dependencyAsConfiguration(group, name, version)
    }

    private fun dependencyAsConfiguration(group: String, name: String, version: String) =
        project.configurations.maybeCreate("${group}_${name}_$version").apply {
            isVisible = false
            defaultDependencies {
                add(project.dependencies.create(group, name).apply { version { strictly(version) } })
            }
        }
}

