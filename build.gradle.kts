plugins {
    kotlin("jvm") version "1.3.21" apply true
    id("tanvd.kosogor") version "1.0.3" apply true
    id("net.researchgate.release") version "2.8.0"
}


val afterRelease = tasks.getByPath("afterReleaseBuild")

subprojects {
    apply(plugin = "tanvd.kosogor")
    apply(plugin = "net.researchgate.release")
    afterEvaluate {
        afterRelease.dependsOn(tasks.getByPath("bintrayUpload"))
    }
}

repositories {
    jcenter()
}
