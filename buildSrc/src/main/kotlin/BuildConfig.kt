 import org.gradle.api.Project

object BuildConfig {
    val MINECRAFT_VERSION: String = "1.21.4"
    val NEOFORGE_VERSION: String = "21.4.3-beta"
    val FABRIC_LOADER_VERSION: String = "0.16.9"
    val FABRIC_API_VERSION: String = "0.110.5+1.21.4"

    // This value can be set to null to disable Parchment.
    // TODO: Re-add Parchment
    val PARCHMENT_VERSION: String? = null

    // https://semver.org/
    var MOD_VERSION: String = "0.6.3"

    fun createVersionString(project: Project): String {
        val builder = StringBuilder()

        val isReleaseBuild = project.hasProperty("build.release")
        val buildId = System.getenv("GITHUB_RUN_NUMBER")

        if (isReleaseBuild) {
            builder.append(MOD_VERSION)
        } else {
            builder.append(MOD_VERSION.substringBefore('-'))
            builder.append("-snapshot")
        }

        builder.append("+mc").append(MINECRAFT_VERSION)

        if (!isReleaseBuild) {
            if (buildId != null) {
                builder.append("-build.${buildId}")
            } else {
                builder.append("-local")
            }
        }

        return builder.toString()
    }
}