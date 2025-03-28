package org.jetbrains.exposed.migration.plugin

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configuration extension for the Exposed Migration Plugin.
 *
 * This extension allows users to configure the behavior of the plugin
 * in their build.gradle.kts file.
 */
open class ExposedMigrationExtension @Inject constructor(objects: ObjectFactory) {

    /**
     * Directory where the generated migration scripts will be stored.
     * Default: src/main/resources/db/migration
     */
    val migrationsDir: DirectoryProperty = objects.directoryProperty()

    /**
     * Package name where Exposed table definitions are located.
     * The plugin will scan this package for table definitions.
     */
    val exposedTablesPackage: Property<String> = objects.property(String::class.java)

    /**
     * Prefix for migration file names.
     * Default: V
     */
    val migrationFilePrefix: Property<String> = objects.property(String::class.java).convention("V")

    /**
     * Separator for migration file names.
     * Default: __
     */
    val migrationFileSeparator: Property<String> = objects.property(String::class.java).convention("__")

    /**
     * File extension for migration files.
     * Default: sql
     */
    val migrationFileExtension: Property<String> = objects.property(String::class.java).convention(".sql")

    /**
     * URL for the database connection.
     * This is optional if useTestContainers is true.
     */
    val databaseUrl: Property<String> = objects.property(String::class.java)

    /**
     * Username for the database connection.
     * This is optional if useTestContainers is true.
     */
    val databaseUser: Property<String> = objects.property(String::class.java)

    /**
     * Password for the database connection.
     * This is optional if useTestContainers is true.
     */
    val databasePassword: Property<String> = objects.property(String::class.java)

    /**
     * Docker image name for TestContainers.
     */
    val testContainersImageName: Property<String> = objects.property(String::class.java)

    /**
     * Classpath that is scanned for Exposed Tables
     */
    val classpath: ConfigurableFileCollection = objects.fileCollection()
}
