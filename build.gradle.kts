import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.report.ReportMergeTask

plugins {
    kotlin("jvm") apply true
    id("com.jfrog.artifactory") version "4.24.21"
    id("org.jetbrains.dokka") version "1.5.31"
    id("io.gitlab.arturbosch.detekt")
}

allprojects {
    apply(from = rootProject.file("buildScripts/gradle/checkstyle.gradle.kts"))

    if (this.name != "exposed-tests" && this.name != "exposed-bom" && this != rootProject) {
        apply(from = rootProject.file("buildScripts/gradle/publishing.gradle.kts"))
    }
}

val reportMerge by tasks.registering(ReportMergeTask::class) {
    output.set(rootProject.buildDir.resolve("reports/detekt/exposed.xml"))
}

subprojects {
    plugins.withType(DetektPlugin::class) {
        tasks.withType(Detekt::class) detekt@{
            finalizedBy(reportMerge)
            reportMerge.configure {
                input.from(this@detekt.xmlReportFile)
            }
        }
    }
}


repositories {
    mavenCentral()
}
