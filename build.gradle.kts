import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig
import groovy.lang.GroovyObject
import org.jetbrains.exposed.gradle.isReleaseBuild
import org.jetbrains.exposed.gradle.setPomMetadata
import org.jfrog.gradle.plugin.artifactory.dsl.ResolverConfig

plugins {
    kotlin("jvm") apply true
    id("org.jetbrains.dokka") version "1.6.10"
    id("com.jfrog.artifactory") version "4.26.2"
    id("io.gitlab.arturbosch.detekt")
    id ("java")
    id("maven-publish")
    idea
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven {
        name = "JFrog"
        url = uri("https://tmpasipanodya.jfrog.io/artifactory/releases")
        credentials {
            username = System.getenv("ARTIFACTORY_USERNAME")
            password = System.getenv("ARTIFACTORY_PASSWORD")
        }
    }
}

allprojects {
    apply(from = rootProject.file("buildScripts/gradle/checkstyle.gradle.kts"))

    if (this.name != "exposed-tests" && this.name != "exposed-bom" && this != rootProject) {
        apply(plugin = "com.jfrog.artifactory")
        apply(plugin = "maven-publish")
        apply(plugin = "java")

        val projekt = this

        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    artifactId = projekt.name
                    from(projekt.components["java"])
                    version = if (isReleaseBuild()) "${projekt.version}" else "${projekt.version}-SNAPSHOT"
                    versionMapping {
                        usage("java-api") {
                            fromResolutionOf("runtimeClasspath")
                        }
                    }
                    pom { setPomMetadata(projekt) }
                }
            }
        }
    }
}

val reportMerge by tasks.registering(ReportMergeTask::class) {
    output.set(rootProject.buildDir.resolve("reports/detekt/exposed.xml"))
}

artifactory {
    setContextUrl("https://tmpasipanodya.jfrog.io/artifactory/")

    publish(delegateClosureOf<PublisherConfig> {
        repository(delegateClosureOf<GroovyObject> {
            setProperty("repoKey", if (isReleaseBuild()) "releases" else "snapshots")
            setProperty("username", System.getenv("ARTIFACTORY_USERNAME"))
            setProperty("password", System.getenv("ARTIFACTORY_PASSWORD"))
            setProperty("maven", true)
        })

        defaults(delegateClosureOf<GroovyObject> {
            invokeMethod("publications", "mavenJava")
        })
    })

    resolve(delegateClosureOf<ResolverConfig> {
        setProperty("repoKey", if (isReleaseBuild()) "releases" else "snapshots")
    })
}

subprojects {
    tasks.withType<Detekt>().configureEach detekt@{
        finalizedBy(reportMerge)
        reportMerge.configure {
            input.from(this@detekt.xmlReportFile)
        }
    }
}
