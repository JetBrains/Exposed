package org.jetbrains.exposed.gradle

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

fun Project.configureDetekt() {
    apply<DetektPlugin>()

    configure<DetektExtension> {
        ignoreFailures = false
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom("$rootDir/detekt/detekt-config.yml")
    }
    tasks.withType<Detekt>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
        }
    }
}
