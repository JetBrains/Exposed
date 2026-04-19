package org.jetbrains.exposed.v1.gradle.plugin

import org.gradle.api.file.DirectoryProperty
import org.gradle.workers.WorkParameters
import java.net.URL

/**
 * Parameter objects for the migrations extension's work actions.
 */
interface GenerateMigrationsParameters : WorkParameters {
    var tablesPackage: String
    var classpathUrls: List<URL>
    val fileDirectory: DirectoryProperty
    var filePrefix: String
    var fileVersionFormat: VersionFormat
    var fileSeparator: String
    var useUpperCaseDescription: Boolean
    var fileExtension: String
    var fullFileName: String?
    var databaseUrl: String?
    var databaseUser: String?
    var databasePassword: String?
    var testContainersImageName: String?
    var debug: Boolean
}
