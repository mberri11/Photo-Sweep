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

    /** `RELEASE.md` lives at the repo root, one level above the module. */
    private val releaseDoc: String by lazy {
        resource("../RELEASE.md", "RELEASE.md").readText()
    }

    /**
     * The value of one `<string>`, as the app will actually render it.
     *
     * `strings.xml` escapes the apostrophe as `\'` because an unescaped one is an Android
     * resource-compiler error; the rendered string has a plain apostrophe, and so does
     * `RELEASE.md`. Unescaping here is what makes a byte-for-byte comparison against the
     * document meaningful rather than a comparison of two different encodings.
     */
    private fun string(name: String): String {
        val m = Regex("""<string name="$name">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(strings)
        requireNotNull(m) { "no <string name=\"$name\"> in strings.xml" }
        return m.groupValues[1].replace("\\'", "'")
    }

    /** Collapses wrapping so a value can be found in a hard-wrapped Markdown paragraph. */
    private fun flat(value: String): String = value.replace(Regex("\\s+"), " ").trim()

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

    /**
     * The duplicates engine is cut from 1.0, and this sentence used to promise it.
     *
     * A permission rationale that describes a feature the build does not contain is a
     * misrepresentation in the one place the app is asking to be trusted. So the claim is
     * pinned byte for byte rather than by keyword: the clause comes back in the same commit
     * that ships the engine, which means it comes back through this test and not by itself.
     */
    @Test
    fun `the analysis claim promises only what the build actually does`() {
        assertTrue(
            "permission_point_analysis_body has drifted from the pinned value; " +
                "see RELEASE.md section 4",
            ANALYSIS_BODY in strings,
        )
    }

    /**
     * The partial-access sentence has exactly one job: say why seeing part of the gallery is
     * not enough.
     *
     * Its replacement is deliberately not a trim of the old line. It is the argument
     * `RELEASE.md` section 1 already makes to Play about the system photo picker — a
     * user-chosen subset cannot answer which folder is eating the storage — so the in-product
     * copy and the declaration form now make one argument instead of two that can drift apart.
     */
    @Test
    fun `the partial-access claim argues from storage, not from duplicates`() {
        assertTrue(
            "permission_partial_body has drifted from the pinned value; " +
                "see RELEASE.md section 4",
            PARTIAL_BODY in strings,
        )
    }

    /**
     * Both rationale sentences used to promise duplicate detection, and the engine is cut from
     * 1.0. The phrase is banned across the whole file rather than checked per string, because
     * the next place it reappears will not be one of the two that already had it.
     */
    @Test
    fun `no string promises duplicate detection`() {
        val lowered = strings.lowercase()
        listOf(DUPLICATES_CLAUSE, "find duplicates").forEach { phrase ->
            assertFalse(
                "strings.xml promises \"$phrase\" while the duplicates engine is still cut " +
                    "from 1.0 — restore the clause in the commit that ships the engine, " +
                    "and update RELEASE.md section 4 in the same commit",
                phrase in lowered,
            )
        }
    }

    /**
     * The claims `RELEASE.md` pins, pinned again from the other side.
     *
     * Two assertions per string, and the second is the one that earns its keep: the value must
     * also appear in `RELEASE.md`. Pinning the literal alone stops the resource drifting from
     * the test; cross-checking the document stops the *document* drifting from the resource,
     * which is the failure that actually matters — the checklist is what gets read before a
     * submission, and a checklist quoting copy the app no longer ships is worse than none.
     *
     * `settings_trash_line` is pinned by section **6**, not 4: the retention claim lives with
     * the trash policy. The rest are section 4.
     */
    @Test
    fun `the strings RELEASE md pins are byte-identical in strings xml`() {
        val pinned = mapOf(
            "settings_trash_line" to
                "Trash is Android's own. Swept photos also appear in Google Photos → Bin.",
            "settings_privacy_body" to
                "Photo Sweep has no upload code. The only network traffic is Google's ad service.",
            "permission_point_analysis_body" to
                "No faces, no places, no labels. Photo Sweep reads file names, dates and sizes " +
                "to build the piles.",
            "permission_partial_body" to
                "It needs to see all of them to tell you which folders are using your storage.",
            "permission_point_privacy_body" to
                "There is no account, no cloud, no upload. Photo Sweep has no code that can " +
                "send a photo anywhere. The only thing that uses the network is Google's ad " +
                "service.",
        )

        val drifted = pinned.filter { (name, expected) -> string(name) != expected }
            .map { (name, expected) -> "$name: expected <$expected> but was <${string(name)}>" }
        assertEquals("these strings drifted from the pinned values", emptyList<String>(), drifted)

        // The distinctive sentence of each claim, as RELEASE.md quotes it.
        val quotedInDoc = listOf(
            "Trash is Android's own. Swept photos also appear in Google Photos → Bin.",
            "Photo Sweep has no upload code. The only network traffic is Google's ad service.",
            "No faces, no places, no labels. Photo Sweep reads file names, dates and sizes to " +
                "build the piles.",
            "It needs to see all of them to tell you which folders are using your storage.",
            "The only thing that uses the network is Google's ad service.",
        )
        val doc = flat(releaseDoc)
        val missing = quotedInDoc.filterNot { flat(it) in doc }
        assertEquals(
            "these in-product claims are not quoted in RELEASE.md; a claim the checklist does " +
                "not carry is a claim nobody re-reads before submitting",
            emptyList<String>(),
            missing,
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
        /** Byte for byte, element and all, including the full stop. */
        const val ANALYSIS_BODY =
            """<string name="permission_point_analysis_body">No faces, no places, no labels. """ +
                """Photo Sweep reads file names, dates and sizes to build the piles.</string>"""

        /** Byte for byte. The argument, not a trim of the duplicates line it replaced. */
        const val PARTIAL_BODY =
            """<string name="permission_partial_body">It needs to see all of them to tell """ +
                """you which folders are using your storage.</string>"""

        /** Lower-case already, so it can be matched against a lowered copy of the file. */
        const val DUPLICATES_CLAUSE =
            "compares images to each other on your phone to find duplicates"

        val XML_COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
        val USES_PERMISSION = Regex("""<uses-permission[^>]*android:name="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
    }
}
