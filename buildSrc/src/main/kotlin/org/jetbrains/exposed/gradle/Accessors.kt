@file:Suppress("FunctionName")

package org.jetbrains.exposed.gradle

import com.avast.gradle.dockercompose.ComposeExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByName

private inline fun <reified T : Any> Project.extByName(name: String): T = extensions.getByName<T>(name)

val Project._dockerCompose
    get() = extByName<ComposeExtension>("dockerCompose")
