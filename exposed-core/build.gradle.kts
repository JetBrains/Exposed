plugins {
    kotlin("multiplatform")
    alias(libs.plugins.dokka)
}

repositories {
    mavenCentral()
}

kotlin{
    jvmToolchain(8)
    jvm {
        compilerOptions {
            optIn.add("kotlin.time.ExperimentalTime")
        }
    }
    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(kotlin("stdlib"))
                api(libs.kotlinx.coroutines)
            }
        }
        val jvmMain by getting {
            dependencies {
                api(kotlin("reflect"))
                api(libs.kotlinx.jvm.datetime)
                api(libs.slf4j)
            }
        }
    }
}
