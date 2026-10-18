import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import java.nio.file.Files
import java.nio.file.LinkOption

plugins {
    id("org.springframework.boot") version "4.1.0"
    id("org.graalvm.buildtools.native") version "1.1.1"
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.spring") version "2.3.20"
}

group = "org.jetbrains.exposed.samples"
version = "1.0.0"

val exposedVersion: String by project

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.jetbrains.exposed:exposed-spring-data:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Without GRAALVM_HOME, Native Build Tools only looks at JAVA_HOME and fails with "Cannot query the value of property 'javaLauncher'"
// if that isn't set either. In that case a native-image capable JDK 25 is taken from the Gradle toolchains: a locally installed
// GraalVM is detected, otherwise one is downloaded through the foojay resolver (see settings.gradle.kts).
if (providers.environmentVariable("GRAALVM_HOME").orNull.isNullOrBlank()) {
    val nativeImageLauncher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
        nativeImageCapable.set(true)
    }
    val provisionedJdks = gradle.gradleUserHomeDir.resolve("jdks")

    graalvmNative {
        toolchainDetection.set(true)
        binaries.configureEach {
            javaLauncher.set(nativeImageLauncher)
        }
    }

    tasks.withType<BuildNativeImageTask>().configureEach {
        doFirst {
            restoreNativeImageLauncher(nativeImageLauncher.get().metadata.installationPath.asFile, provisionedJdks)
        }
    }
}

/**
 * Gradle extracts provisioned toolchains without symbolic links (https://github.com/gradle/gradle/issues/28583): in GraalVM and
 * Mandrel distributions `bin/native-image` is a link to `lib/svm/bin/native-image` and ends up as an empty file. `native-image`
 * then "succeeds" without output, and `nativeRun` / `nativeTest` fail because the executable doesn't exist. This restores the link
 * in toolchains provisioned by Gradle.
 */
fun restoreNativeImageLauncher(javaHome: File, provisionedJdks: File) {
    if (!javaHome.canonicalFile.startsWith(provisionedJdks.canonicalFile)) return
    val launcher = javaHome.resolve("bin/native-image").toPath()
    val target = javaHome.resolve("lib/svm/bin/native-image")
    if (target.isFile && Files.isRegularFile(launcher, LinkOption.NOFOLLOW_LINKS) && Files.size(launcher) == 0L) {
        logger.lifecycle("Restoring the native-image link in the Gradle provisioned toolchain $javaHome")
        Files.delete(launcher)
        Files.createSymbolicLink(launcher, launcher.parent.relativize(target.toPath()))
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
