package com.simobr.photosweep.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Where ads may and may not appear, enforced by reading the source.
 *
 * This is an architectural rule, not a behavioural one: there is no output to assert on, because
 * the bug being prevented is an ad view *existing* on a screen it has no business being on. By
 * the time that is visible it has already shipped.
 *
 * The rules are the ones RELEASE.md and the product brief commit to:
 *
 *  - one banner, on the success screen, and nowhere else;
 *  - no ad of any kind on sweep, confirm, trash or settings;
 *  - nothing gating Restore, Empty Trash, Undo, or the confirm screen;
 *  - no app-open ad and no rewarded ad anywhere in the app.
 */
class AdPlacementTest {

    private val module: File by lazy {
        listOf(File("src"), File("app/src"))
            .firstOrNull { it.isDirectory }
            ?.absoluteFile
            ?.parentFile
            ?: error("could not locate the app module from ${File("").absolutePath}")
    }

    private fun sources(variant: String): List<File> =
        File(module, "src/$variant/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    private fun File.code(): String = readText()
        .replace(BLOCK_COMMENT, "")
        .replace(LINE_COMMENT, "")

    /** Every main-variant source that is not part of the `ads/` package itself. */
    private val outsideAds: List<File> by lazy {
        sources("main").filterNot { it.path.contains("/ads/") }
    }

    @Test
    fun `the ad sdk is referenced only inside the ads package`() {
        val offenders = outsideAds
            .filter { file -> AD_SDK_MARKERS.any { it in file.code() } }
            .map { it.name }

        assertEquals(
            "the Mobile Ads SDK must not leak out of ads/ — a screen that imports it is a " +
                "screen that can grow an ad view",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `no ad view is constructed on any screen other than Success`() {
        val offenders = outsideAds
            .filter { file -> AD_VIEW_MARKERS.any { it in file.code() } }
            .map { it.name }

        // PhotoSweepRoot is the one permitted call site: it passes the banner into the success
        // route as a slot. Nothing else may name an ad composable at all.
        assertEquals(
            "only PhotoSweepRoot.kt may name an ad composable, and only for the Success route",
            listOf("PhotoSweepRoot.kt"),
            offenders,
        )
    }

    /** The forbidden screens, by name, each checked for any ad marker at all. */
    @Test
    fun `sweep confirm trash and settings contain no ad code`() {
        val forbidden = listOf("/ui/sweep/", "/ui/confirm/", "/ui/trash/", "/ui/settings/")
        val offenders = sources("main")
            .filter { file -> forbidden.any { it in file.path } }
            .filter { file ->
                val code = file.code()
                (AD_SDK_MARKERS + AD_VIEW_MARKERS).any { it in code }
            }
            .map { it.name }

        assertEquals(
            "a banner under the thumb on sweep is an accidental-click factory, and a misclick " +
                "on trash costs somebody their photos",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the banner reaches the success screen as a slot, not as an import`() {
        val screen = File(module, "src/main/java/com/simobr/photosweep/ui/success/SuccessScreen.kt")
        assertTrue("SuccessScreen.kt not found", screen.isFile)

        val code = screen.code()
        assertTrue(
            "the banner must arrive as a composable slot parameter",
            "banner: @Composable () -> Unit" in code,
        )
        (AD_SDK_MARKERS + AD_VIEW_MARKERS).forEach { marker ->
            assertTrue("SuccessScreen must not reference '$marker'", marker !in code)
        }
    }

    /**
     * Formats this stage explicitly refuses to build. Asserted as absences so that adding one
     * is a failing test rather than a code review nobody asked for.
     */
    @Test
    fun `no app-open ad and no rewarded ad exist anywhere`() {
        val banned = listOf("AppOpenAd", "RewardedAd", "RewardedInterstitialAd", "NativeAd")
        val offenders = sources("main")
            .filter { file -> banned.any { it in file.code() } }
            .map { it.name }

        assertEquals(
            "app-open, rewarded and native formats are out of scope for 1.0",
            emptyList<String>(),
            offenders,
        )
    }

    /** Restore, Empty Trash, Undo and Confirm are never behind an ad. */
    @Test
    fun `no safety or destructive action is gated on an ad`() {
        // Scanned outside ads/ — that package declares these functions, so including it would
        // only assert that the definitions exist where they are defined.
        val gatedFiles = outsideAds
            .filter { file ->
                val code = file.code()
                "showThenContinue" in code || "mayShowInterstitial" in code
            }
            .map { it.name }
            .sorted()

        assertEquals(
            "the interstitial may only be reached from the Success route in PhotoSweepRoot.kt",
            listOf("PhotoSweepRoot.kt"),
            gatedFiles,
        )
    }

    private companion object {
        val AD_SDK_MARKERS = listOf(
            "com.google.android.gms.ads",
            "com.google.android.ump",
            "MobileAds",
            "AdRequest",
        )
        val AD_VIEW_MARKERS = listOf("SuccessBanner", "SuccessInterstitial", "AdView")
        val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("//[^\n]*")
    }
}
