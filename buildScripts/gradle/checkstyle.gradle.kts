import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.DetektPlugin

apply<DetektPlugin>()

configure<DetektExtension> {
    ignoreFailures = false
    buildUponDefaultConfig = true
    config = files(
        rootDir.resolve("detekt.yml").takeIf { it.isFile },
        projectDir.resolve("detekt.yml").takeIf { it.isFile }
    )
    reports {
        xml.enabled = true
        html.enabled = false
        txt.enabled = false
        sarif.enabled = false
    }
    parallel = true
}
