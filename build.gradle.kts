plugins {
    kotlin("jvm") version "1.4.21" apply true
    id("tanvd.kosogor") version "1.0.9" apply true
}

subprojects {
    apply(plugin = "tanvd.kosogor")
}

repositories {
    jcenter()
}
