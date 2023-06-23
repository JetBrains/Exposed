import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension

apply<DetektPlugin>()

configure<DetektExtension> {
    ignoreFailures = false
    buildUponDefaultConfig = true
    config = files(
        rootDir.resolve("detekt/detekt-config.yml").takeIf { it.isFile },
        projectDir.resolve("detekt/detekt-config.yml").takeIf { it.isFile }
    )
    reports {
        xml.enabled = true
        html.enabled = false
        txt.enabled = false
        sarif.enabled = false
    }
    parallel = true
}
