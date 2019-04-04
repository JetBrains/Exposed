plugins {
    kotlin("jvm") version "1.3.21" apply true
    id("tanvd.kosogor") version "1.0.3" apply true
}

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
