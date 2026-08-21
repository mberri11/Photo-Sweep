package com.simobr.photosweep.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The permission set and the promises made about it are both product decisions with legal
 * weight, and both are one careless edit away from being wrong. Neither shows up in any
 * behavioural test, so they are pinned here.
 */
class PermissionManifestAndCopyTest {

    private fun resource(vararg candidates: String): File =
        candidates.map(::File).firstOrNull { it.isFile }
            ?: error("not found from ${File("").absolutePath}: ${candidates.toList()}")

    private val manifest: String by lazy {
        resource("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
            .readText()
            .replace(XML_COMMENT, "")
    }

    private val strings: String by lazy {
        resource("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml")
            .readText()
            .replace(XML_COMMENT, "")
    }

    // ---- the declared set --------------------------------------------------------------

    @Test
    fun `the manifest declares exactly the intended permissions`() {
        val declared = USES_PERMISSION.findAll(manifest).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf(
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
                "com.google.android.gms.permission.AD_ID",
            ),
            declared,
        )
    }

    @Test
    fun `the legacy storage permission is capped below api 33`() {
        // Uncapped, it would be requested on modern Android where the granular media
        // permissions exist, and the Data safety form would no longer match the manifest.
        assertTrue(
            "READ_EXTERNAL_STORAGE must carry maxSdkVersion=\"32\"",
            Regex(
                """READ_EXTERNAL_STORAGE"\s*\n?\s*android:maxSdkVersion="32"""",
            ).containsMatchIn(manifest),
        )
    }

    @Test
    fun `the deliberately omitted permissions are still omitted`() {
        listOf(
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.MANAGE_MEDIA",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        ).forEach { permission ->
            assertFalse(
                "$permission is declared; see RELEASE.md before adding it",
                permission in manifest,
            )
        }
    }

    // ---- the claims ----------------------------------------------------------------------

    /**
     * The earlier rationale copy claimed the app shipped without the internet permission.
     * That is false in any build containing AdMob, and it is false in a way the user can
     * confirm from the very settings screen the copy points them at.
     */
    @Test
    fun `no string claims the app lacks the internet permission`() {
        val banned = listOf(
            "no internet permission",
            "without the internet permission",
            "cannot send anything anywhere",
            "check it yourself",
        )
        val lowered = strings.lowercase()
        banned.forEach { phrase ->
            assertFalse(
                "strings.xml still contains the false claim \"$phrase\" — " +
                    "the Mobile Ads SDK requires INTERNET; see RELEASE.md section 3",
                phrase in lowered,
            )
        }
    }

    @Test
    fun `the privacy copy names the ad service as the network user`() {
        // The honest version of the claim has to stay honest: if the sentence about ads is
        // deleted, what remains reads as "nothing touches the network", which is false.
        assertTrue(
            "the privacy promise must name Google's ad service as the one network user",
            strings.contains("The only thing that uses the network is Google\\'s ad service."),
        )
        assertTrue(
            strings.contains("The only network traffic is Google\\'s ad service."),
        )
    }

    @Test
    fun `the internet permission is declared, which is why the copy has to say so`() {
        assertTrue("android.permission.INTERNET" in manifest)
    }

    /**
     * The Mobile Ads SDK's content provider reads this at process start and throws if it is
     * absent, killing the app before any of its own code runs. It cost a device launch to
     * find, because nothing before that had ever started the process.
     */
    @Test
    fun `the admob application id meta-data is declared`() {
        assertTrue(
            "MobileAdsInitProvider crashes the process at startup without this meta-data",
            "com.google.android.gms.ads.APPLICATION_ID" in manifest,
        )
        assertTrue(
            "the App ID must stay a build-type placeholder so release cannot ship the sample ID",
            Regex("""android:value="\$\{admobAppId}"""").containsMatchIn(manifest),
        )
    }

    private companion object {
        val XML_COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
        val USES_PERMISSION = Regex("""<uses-permission[^>]*android:name="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
    }
}
