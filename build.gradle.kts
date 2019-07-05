plugins {
    kotlin("jvm") version "1.3.31" apply true
    id("tanvd.kosogor") version "1.0.7" apply true
}

subprojects {
    apply(plugin = "tanvd.kosogor")
}

repositories {
    jcenter()
}
