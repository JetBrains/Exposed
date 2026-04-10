package org.jetbrains.exposed.v1.gradle.plugin

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configuration of the migrations extension for the Exposed Gradle Plugin.
 *
 * This extension allows users to configure the behavior of the plugin
 * in their `build.gradle.kts` file in a `migrations` block.
 */
open class MigrationsExtension @Inject constructor(objects: ObjectFactory) {

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

const val GENERATE_MIGRATIONS_TASK_NAME = "generateMigrations"
const val GENERATE_MIGRATIONS_TASK_DESCRIPTION = "Generates SQL migration scripts from Exposed table definitions"
