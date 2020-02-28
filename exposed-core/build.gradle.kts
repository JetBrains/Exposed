import tanvd.kosogor.proxy.publishJar

plugins {
    kotlin("jvm") apply true
    id("org.gradle.maven-publish")
}

repositories {
    jcenter()
}

dependencies {
    api(kotlin("stdlib"))
    api(kotlin("reflect"))
    api("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.3.3")
    api("org.slf4j", "slf4j-api", "1.7.25")
    compileOnly("com.h2database", "h2", "1.4.199")
}

group = "in.porter.exposed"
version = "0.21.1"

val sourceJar = task("sourceJar", Jar::class) {
    dependsOn(JavaPlugin.CLASSES_TASK_NAME)
    archiveClassifier.set("sources")
    from(project.the<SourceSetContainer>()["main"].allSource)
}

publishing {
    publications {
        create<MavenPublication>("exposed-core") {
            from(components["java"])
            artifact(sourceJar)
        }
    }

    repositories {
        maven {
            url = uri("s3://porter-maven/releases")
            authentication {
                create<AwsImAuthentication>("awsIm")
            }
        }
    }
}
