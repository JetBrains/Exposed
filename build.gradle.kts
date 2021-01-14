plugins {
    kotlin("jvm") version "1.4.21" apply true
    id("org.jmailen.kotlinter") version "3.3.0"
    id("tanvd.kosogor") version "1.0.9" apply true
}

allprojects {
    apply(plugin = "org.jmailen.kotlinter")
}

subprojects {
    apply(plugin = "tanvd.kosogor")
}

repositories {
    jcenter()
}
