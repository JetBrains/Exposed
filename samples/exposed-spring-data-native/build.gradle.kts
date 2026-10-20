import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths

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
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.jetbrains.exposed:exposed-spring-data-starter:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Without GRAALVM_HOME, Native Build Tools only looks at JAVA_HOME and fails with "Cannot query the value of property 'javaLauncher'"
// if that isn't set either. In that case GraalVM Community 25 is taken from the Gradle toolchains: a locally installed one is
// detected, otherwise it is downloaded through the foojay resolver (see settings.gradle.kts). The vendor is fixed, because without
// it foojay prefers Mandrel. Other native-image distributions (Oracle GraalVM, Mandrel, Liberica NIK) can be used with GRAALVM_HOME.
if (providers.environmentVariable("GRAALVM_HOME").orNull.isNullOrBlank()) {
    val nativeImageLauncher = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.GRAAL_VM)
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
        doFirst(RestoreNativeImageLinks(nativeImageLauncher, provisionedJdks))
    }
}

/**
 * Gradle extracts provisioned toolchains without symbolic links (https://github.com/gradle/gradle/issues/28583): in GraalVM
 * `bin/native-image` is a link to `lib/svm/bin/native-image` and ends up as an empty file. `native-image` then "succeeds" without
 * output, and `nativeRun` / `nativeTest` fail because the executable doesn't exist. This restores the native-image links of the
 * GraalVM distribution ([LINKS]), only in toolchains provisioned by Gradle (below `~/.gradle/jdks`) and only where the link is an
 * empty regular file and its target exists.
 *
 * A class with only a launcher provider and a file, instead of a lambda calling script functions, keeps the configuration cache
 * working.
 */
class RestoreNativeImageLinks(private val launcher: Provider<JavaLauncher>, private val provisionedJdks: File) : Action<Task> {

    override fun execute(task: Task) {
        val javaHome = launcher.get().metadata.installationPath.asFile.canonicalFile
        if (!javaHome.startsWith(provisionedJdks.canonicalFile)) return
        for ((linkPath, targetPath) in LINKS) {
            val link = javaHome.toPath().resolve(linkPath)
            val target = link.resolveSibling(targetPath)
            if (Files.isRegularFile(link, LinkOption.NOFOLLOW_LINKS) && Files.size(link) == 0L && Files.isRegularFile(target)) {
                task.logger.lifecycle("Restoring the link $linkPath -> $targetPath in the Gradle provisioned toolchain $javaHome")
                Files.deleteIfExists(link)
                try {
                    Files.createSymbolicLink(link, Paths.get(targetPath))
                } catch (ignored: FileAlreadyExistsException) {
                    // restored by a concurrent build
                }
            }
        }
    }

    companion object {
        /** Symbolic links of the GraalVM 25 distribution, relative to the Java home; `lib/svm/bin` comes first because `bin` links to it. */
        val LINKS = listOf(
            "lib/svm/bin/native-image-configure" to "native-image-utils",
            "bin/native-image" to "../lib/svm/bin/native-image",
            "bin/native-image-configure" to "../lib/svm/bin/native-image-configure",
            "bin/native-image-utils" to "../lib/svm/bin/native-image-utils",
            "LICENSE_NATIVEIMAGE.txt" to "lib/svm/LICENSE_NATIVEIMAGE.txt",
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
