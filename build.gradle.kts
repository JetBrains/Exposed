plugins {
    kotlin("jvm") version "1.3.50" apply true
    id("tanvd.kosogor") version "1.0.7" apply true
}

subprojects {
    apply(plugin = "tanvd.kosogor")
}

repositories {
    jcenter()
}
