plugins {
    kotlin("jvm") version "1.3.21" apply true
    id("tanvd.kosogor") version "1.0.3" apply true
    id("net.researchgate.release") version "2.8.0"
}

subprojects {
    apply(plugin = "tanvd.kosogor")
    apply(plugin = "net.researchgate.release")
    afterEvaluate {
        tasks.getByPath("afterReleaseBuild").dependsOn(tasks.getByPath("bintrayUpload"))
    }
}

repositories {
    jcenter()
}
