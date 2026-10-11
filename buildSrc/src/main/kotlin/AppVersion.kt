/**
 * The app's release version as Google Play and F-Droid read it. The code packs
 * `MAJOR.MINOR.PATCH` into `MAJOR * 1_000_000 + MINOR * 1_000 + PATCH`, so every release gets a
 * strictly greater `versionCode` than its predecessor; minor and patch must therefore stay below
 * 1000. F-Droid reads both values with a regex from `version.properties`, hence the fixed,
 * comment-free format.
 */
data class AppVersion(
    val name: String,
    val code: Int,
) {
    fun toProperties(): String = "versionName=$name\nversionCode=$code\n"

    companion object {
        private val SEMANTIC_VERSION = Regex("""(\d+)\.(\d+)\.(\d+)""")

        fun fromSemanticVersion(semanticVersion: String): AppVersion {
            val (major, minor, patch) =
                requireNotNull(SEMANTIC_VERSION.matchEntire(semanticVersion)) {
                    "Version must look like MAJOR.MINOR.PATCH, was '$semanticVersion'"
                }.destructured.toList().map(String::toInt)
            require(minor < 1_000 && patch < 1_000) {
                "Version '$semanticVersion' does not fit the versionCode scheme: minor and patch must be below 1000"
            }
            return AppVersion(semanticVersion, major * 1_000_000 + minor * 1_000 + patch)
        }

        fun parseProperties(text: String): AppVersion {
            val values =
                text
                    .lines()
                    .filter { '=' in it }
                    .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
            val name = requireNotNull(values["versionName"]) { "version.properties lacks versionName" }
            val codeText = requireNotNull(values["versionCode"]) { "version.properties lacks versionCode" }
            val code =
                requireNotNull(codeText.toIntOrNull()) {
                    "version.properties has a non-numeric versionCode '$codeText'"
                }
            val expected = fromSemanticVersion(name)
            require(expected.code == code) {
                "version.properties pairs versionName $name with versionCode $code, expected ${expected.code}"
            }
            return expected
        }
    }
}
