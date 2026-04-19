package org.jetbrains.exposed.v1.gradle.plugin

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.exposed.v1.gradle.plugin.ExposedGradlePlugin.Companion.TASK_GROUP
import javax.inject.Inject

/**
 * Configuration of the migrations extension for the Exposed Gradle Plugin.
 *
 * This extension allows users to configure the behavior of the plugin
 * in their `build.gradle.kts` file in a nested `migrations` block.
 */
open class MigrationsExtension @Inject internal constructor(objects: ObjectFactory) {

    /**
     * Package name where Exposed table definitions are located.
     * The plugin will scan this package for table definitions.
     */
    val tablesPackage: Property<String> = objects.property(String::class.java)

    /**
     * Classpath that is scanned for Exposed table definitions.
     * Defaults to the project's runtime classpath.
     */
    val classpath: ConfigurableFileCollection = objects.fileCollection()

    /**
     * Directory where the generated migration scripts will be stored.
     * Defaults to `src/main/resources/db/migration`.
     */
    val fileDirectory: DirectoryProperty = objects.directoryProperty()

    /**
     * Prefix for migration script names.
     * Defaults to `V`.
     */
    val filePrefix: Property<String> = objects.property(String::class.java).convention("V")

    /**
     * Version format for migration script names.
     * Defaults to using the full current timestamp (with seconds) in the format YYYYMMDDHHMMSS.
     */
    val fileVersionFormat: Property<VersionFormat> = objects.property(VersionFormat::class.java)
        .convention(VersionFormat.TIMESTAMP_ONLY)

    /**
     * Separator for migration script names.
     * Defaults to `__`.
     */
    val fileSeparator: Property<String> = objects.property(String::class.java).convention("__")

    /**
     * Whether the descriptive part of migration script names should be all in upper-case.
     * Defaults to `true`.
     */
    val useUpperCaseDescription: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    /**
     * File extension for migration scripts.
     * Defaults to `.sql`.
     */
    val fileExtension: Property<String> = objects.property(String::class.java).convention(".sql")

    /**
     * URL for the database connection, which should be considered as the current schema.
     * This is optional only if `testContainersImageName` is not set.
     */
    val databaseUrl: Property<String> = objects.property(String::class.java)

    /**
     * Username for the database connection, which should be considered as the current schema.
     * This is optional only if `testContainersImageName` is not set.
     */
    val databaseUser: Property<String> = objects.property(String::class.java)

    /**
     * Password for the database connection, which should be considered as the current schema.
     * This is optional only if `testContainersImageName` is not set.
     */
    val databasePassword: Property<String> = objects.property(String::class.java)

    /**
     * Docker image name for when using TestContainers to apply existing scripts before generating new ones.
     * This is optional only if no values are set for any database properties.
     */
    val testContainersImageName: Property<String> = objects.property(String::class.java)

    companion object {
        const val NAME: String = "migrations"
    }
}

internal fun Project.configureMigrations() {
    val extension = createExposedExtension<MigrationsExtension>(MigrationsExtension.NAME)

    // Set default migration directory
    extension.fileDirectory.convention(
        layout.projectDirectory.dir("src/main/resources/db/migration")
    )

    // Set default classpath for Exposed tables
    val classpath = extensions
        .findByType(SourceSetContainer::class.java)
        ?.findByName("main")
        ?.runtimeClasspath

    if (classpath != null) extension.classpath.setFrom(classpath)

    tasks.register(GENERATE_MIGRATIONS_TASK_NAME, GenerateMigrationsTask::class.java) {
        it.description = GENERATE_MIGRATIONS_TASK_DESCRIPTION
        it.group = TASK_GROUP

        it.tablesPackage.set(extension.tablesPackage)
        it.classpath.setFrom(extension.classpath.toList())

        it.fileDirectory.set(extension.fileDirectory)
        it.filePrefix.set(extension.filePrefix)
        it.fileVersionFormat.set(extension.fileVersionFormat)
        it.fileSeparator.set(extension.fileSeparator)
        it.useUpperCaseDescription.set(extension.useUpperCaseDescription)
        it.fileExtension.set(extension.fileExtension)

        it.databaseUrl.set(extension.databaseUrl)
        it.databaseUser.set(extension.databaseUser)
        it.databasePassword.set(extension.databasePassword)

        it.testContainersImageName.set(extension.testContainersImageName)
    }
}
