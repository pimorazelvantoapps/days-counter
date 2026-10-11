import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AppVersionTest {
    @Test
    fun `semantic version packs major minor and patch into the version code`() {
        assertEquals(AppVersion("1.2.3", 1_002_003), AppVersion.fromSemanticVersion("1.2.3"))
    }

    @Test
    fun `semantic version with fewer than three parts is rejected`() {
        val error = assertThrows<IllegalArgumentException> { AppVersion.fromSemanticVersion("1.2") }
        assertEquals("Version must look like MAJOR.MINOR.PATCH, was '1.2'", error.message)
    }

    @Test
    fun `semantic version with a pre-release suffix is rejected`() {
        assertThrows<IllegalArgumentException> { AppVersion.fromSemanticVersion("1.2.3-beta.1") }
    }

    @Test
    fun `minor of 1000 does not fit the version code scheme`() {
        val error = assertThrows<IllegalArgumentException> { AppVersion.fromSemanticVersion("1.1000.0") }
        assertEquals(
            "Version '1.1000.0' does not fit the versionCode scheme: minor and patch must be below 1000",
            error.message,
        )
    }

    @Test
    fun `patch of 1000 does not fit the version code scheme`() {
        assertThrows<IllegalArgumentException> { AppVersion.fromSemanticVersion("1.0.1000") }
    }

    @Test
    fun `properties text holds exactly name and code`() {
        assertEquals("versionName=1.0.7\nversionCode=1000007\n", AppVersion("1.0.7", 1_000_007).toProperties())
    }

    @Test
    fun `properties text parses back into the same version`() {
        val version = AppVersion.fromSemanticVersion("2.5.9")
        assertEquals(version, AppVersion.parseProperties(version.toProperties()))
    }

    @Test
    fun `properties text without version code is rejected`() {
        val error = assertThrows<IllegalArgumentException> { AppVersion.parseProperties("versionName=1.0.7\n") }
        assertEquals("version.properties lacks versionCode", error.message)
    }

    @Test
    fun `properties text with a non-numeric version code is rejected`() {
        val error =
            assertThrows<IllegalArgumentException> {
                AppVersion.parseProperties("versionName=1.0.7\nversionCode=seven\n")
            }
        assertEquals("version.properties has a non-numeric versionCode 'seven'", error.message)
    }

    @Test
    fun `properties text whose code does not match its name is rejected`() {
        val error =
            assertThrows<IllegalArgumentException> {
                AppVersion.parseProperties("versionName=1.0.7\nversionCode=1000008\n")
            }
        assertEquals("version.properties pairs versionName 1.0.7 with versionCode 1000008, expected 1000007", error.message)
    }
}
