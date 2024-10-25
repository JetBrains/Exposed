plugins {
    id("java")

    alias(libs.plugins.dokka)
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":exposed-core"))

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}
