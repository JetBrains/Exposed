plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSluginSpring)
    alias(libs.plugins.orgSpringframeworkBoot)
    alias(libs.plugins.ioSpringDependencyManagement)
    alias(libs.plugins.orgGraalvmBuildtoolsNative)
}

group = "org.jetbrains.exposed.samples.springboot3.r2dbc"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

graalvmNative {
    binaries {
        named("main") {
            // TODO https://github.com/netty/netty/issues/15331
            buildArgs.add("--initialize-at-run-time=sun.net.dns.ResolverConfigurationImpl")
            buildArgs.add("-O2")
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.springBootStarterWebFlux) {
        exclude(group = libs.springBootStarterReactorNetty.get().group, module = libs.springBootStarterReactorNetty.get().name)
    }
    implementation(libs.springBootStarterJetty)
    implementation(libs.jacksonModuleKotlin)
    implementation(libs.reactorKotlinExtensions)
    implementation(libs.kotlinReflect)
    implementation(libs.kotlinxCoroutinesReactor)

    // R2DBC
    implementation(libs.r2dbcMysql)
    implementation(libs.exposedSpringbootStarter)
    implementation(libs.exposedR2dbc)
    implementation(libs.springBootStarterDataR2dbc)

    // TODO remove issue https://github.com/JetBrains/Exposed/pull/2501
    implementation("org.postgresql:r2dbc-postgresql:1.0.7.RELEASE")

    testImplementation(libs.springBootStarterTest)
    testImplementation(libs.reactorTest)
    testImplementation(libs.kotlinTestJunit5)
    testImplementation(libs.kotlinxCoroutinesTest)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
