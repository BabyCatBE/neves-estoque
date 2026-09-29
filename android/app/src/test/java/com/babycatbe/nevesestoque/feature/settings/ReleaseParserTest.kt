package com.babycatbe.nevesestoque.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseParserTest {
    private val sha = "a".repeat(64)

    private fun releaseJson(
        body: String = "versionCode: 26",
        apkName: String = ReleaseParser.OFFICIAL_APK,
        checksumName: String = ReleaseParser.CHECKSUMS,
    ): String {
        val escapedBody = body
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        return """{"tag_name":"v0.26.0-alpha01","body":"$escapedBody","draft":false,"prerelease":false,"assets":[{"name":"$apkName","browser_download_url":"https://example.test/app.apk"},{"name":"$checksumName","browser_download_url":"https://example.test/SHA256SUMS"}]}"""
    }

    @Test
    fun versionCodeIsPrimary() {
        assertTrue(ReleaseParser.isUpdateAvailable(25, 26))
        assertFalse(ReleaseParser.isUpdateAvailable(25, 25))
        assertFalse(ReleaseParser.isUpdateAvailable(25, 24))
    }

    @Test
    fun parsesOfficialAssetsAndChecksum() {
        val parsed = ReleaseParser.parse(releaseJson(), "$sha  ${ReleaseParser.OFFICIAL_APK}\n")
        assertEquals(26, parsed.versionCode)
        assertEquals("0.26.0-alpha01", parsed.versionName)
        assertEquals(ReleaseParser.OFFICIAL_APK, parsed.apk.name)
        assertEquals(sha, parsed.sha256)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMissingOfficialApk() {
        ReleaseParser.parse(releaseJson(apkName = "app-release.apk"), "$sha  app-release.apk\n")
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsMissingVersionCode() {
        ReleaseParser.parse(releaseJson(body = "Sem metadado obrigatório"), "$sha  ${ReleaseParser.OFFICIAL_APK}\n")
    }

    @Test
    fun validatesSha256() {
        val file = File.createTempFile("neves-update", ".apk")
        try {
            file.writeText("apk-test")
            val expected = ReleaseParser.sha256(file)
            assertTrue(ReleaseParser.checksumMatches(file, expected.uppercase()))
            assertFalse(ReleaseParser.checksumMatches(file, "0".repeat(64)))
        } finally {
            file.delete()
        }
    }
}
