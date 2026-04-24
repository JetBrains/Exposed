package org.jetbrains.exposed.v1.gradle.plugin

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import javax.inject.Inject

/**
 * The root extension for the Exposed Gradle Plugin.
 *
 * This extension allows users to configure the behavior of the plugin and its features
 * in their `build.gradle.kts` file in an `exposed` block.
 * This root extension currently has no properties of its own and is intended as an extension-aware
 * container for the [MigrationsExtension] and any future feature extensions.
 */
open class ExposedExtension @Inject internal constructor(objects: ObjectFactory) {
    companion object {
        const val NAME: String = "exposed"
    }
}

internal inline fun <reified T : Any> Project.createExposedExtension(name: String): T = exposedExtensions
    .create(name, T::class.java, project.objects)

internal val Project.exposedExtensions: ExtensionContainer
    get() = (exposedExtension as ExtensionAware).extensions

private val Project.exposedExtension: ExposedExtension
    get() = extensions.getByType(ExposedExtension::class.java)
