import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.exposed.gradle.Versions
import org.jetbrains.exposed.gradle.configureDetekt
import org.jetbrains.exposed.gradle.configurePublishing
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm") apply true
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.13.2"

    id("com.avast.gradle.docker-compose")
}

repositories {
    mavenLocal()
    mavenCentral()
}

allprojects {
    configureDetekt()

    if (this.name != "exposed-tests" && this.name != "exposed-bom" && this != rootProject) {
        configurePublishing()
    }
}

apiValidation {
    ignoredProjects.addAll(listOf("exposed-tests", "exposed-bom"))
}

val reportMerge by tasks.registering(ReportMergeTask::class) {
    output.set(rootProject.buildDir.resolve("reports/detekt/exposed.xml"))
}

subprojects {
    dependencies {
        detektPlugins("io.gitlab.arturbosch.detekt", "detekt-formatting", "1.21.0")
    }
    tasks.withType<Detekt>().configureEach detekt@{
        onlyIf { this@subprojects.name !== "exposed-tests" }

        finalizedBy(reportMerge)
        reportMerge.configure {
            input.from(this@detekt.xmlReportFile)
        }
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "1.8"
            apiVersion = "1.6"
            languageVersion = "1.6"
        }
    }
}

class DB(val name: String, val port: Int, val dependency: String)

val dbs = listOf(
    DB("mysql", 3306, "mysql:mysql-connector-java:${Versions.mysql51}"),
    DB("mariadb", 3306, "org.mariadb.jdbc:mariadb-java-client:${Versions.mariaDB_v2}")
)

dockerCompose {
    dbs.forEach {
        nested(it.name).apply {
            useComposeFiles.set(listOf("buildScripts/docker/docker-compose-${it.name}.yml"))
            stopContainers.set(false)
        }
    }
}

subprojects {
    if (name == "exposed-bom") return@subprojects

    apply(plugin = "org.jetbrains.kotlin.jvm")

    val testTasks = dbs.map {
        tasks.register<Test>("test${it.name.capitalized()}") {

            dependsOn(rootProject.tasks.getByName("${it.name}ComposeUp"))
            finalizedBy(rootProject.tasks.getByName("${it.name}ComposeDown"))
            systemProperties["exposed.test.dialects"] = it.name
            systemProperties["exposed.test.db.port"] = it.port.toString()

            description = "Runs tests using $it database"
            group = "verification"
        }
    }

    dependencies {
        dbs.forEach {
            testRuntimeOnly(it.dependency)
        }
    }

    val test by tasks
    test.dependsOn(testTasks)
}
