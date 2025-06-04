package org.jetbrains.exposed.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class DBTestingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // This plugin is used to configure database testing for the Exposed project
        // The actual implementation is in TestDbDsl.kt
    }
}
