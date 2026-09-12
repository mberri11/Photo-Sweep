package com.simobr.photosweep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The seeder and the pile-scope filter are development tools, and a development tool that
 * reaches a shipped build is a liability rather than a convenience.
 *
 * They are kept out by construction, not by a flag: the seeder and the scope filter are
 * compiled only into the debug variant, and `src/release/` supplies a `DebugPileTools` that
 * does nothing. This test asserts the construction, because the construction is the only
 * reason the guarantee holds — `BuildConfig.DEBUG` around a call site still leaves the class,
 * its strings and its filter in the release artifact for anyone who looks.
 *
 * The APK-level proof (`aapt2 dump strings`) is run at the end of each stage. This is the
 * part that fails in CI the moment somebody moves a file back.
 */
class DebugIsolationTest {

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

    /** Rules about what the code *does*. A scan that fires on its own KDoc gets deleted. */
    private fun File.code(): String = readText()
        .replace(BLOCK_COMMENT, "")
        .replace(LINE_COMMENT, "")

    private fun File.xml(): String = readText().replace(XML_COMMENT, "")

    @Test
    fun `the seeder is compiled into the debug variant only`() {
        val debug = sources("debug").map { it.name }
        assertTrue("GallerySeeder.kt must live in src/debug/java", "GallerySeeder.kt" in debug)

        val offenders = (sources("main") + sources("release"))
            .filter { "GallerySeeder" in it.code() }
            .map { it.name }

        assertEquals(
            "the seeder must not be referenced outside the debug variant",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `the release pile query path has no test-gallery filter`() {
        val stub = File(module, "src/release/java/com/simobr/photosweep/debug/DebugPileTools.kt")
        assertTrue("the release DebugPileTools stub is missing", stub.isFile)

        val code = stub.code()
        FILTER_MARKERS.forEach { marker ->
            assertFalse(
                "the release stub references '$marker'; it must know nothing about scoping",
                marker in code,
            )
        }
        assertTrue(
            "the release stub must pass the gallery through untouched",
            "fun scopePhotos(photos: List<Photo>): List<Photo> = photos" in code,
        )
    }

    @Test
    fun `the pile host itself carries no scope filter`() {
        val host = File(module, "src/main/java/com/simobr/photosweep/ui/piles/PileHostScreen.kt")
        assertTrue("PileHostScreen.kt not found", host.isFile)

        val code = host.code()
        FILTER_MARKERS.forEach { marker ->
            assertFalse("PileHostScreen references '$marker'", marker in code)
        }
        assertTrue(
            "the host must route its query through DebugPileTools",
            "DebugPileTools.scopePhotos(photos)" in code,
        )
    }

    /** Both variants must offer the same surface, or the shared call sites stop compiling. */
    @Test
    fun `both variants declare the same DebugPileTools entry points`() {
        listOf("debug", "release").forEach { variant ->
            val file = File(module, "src/$variant/java/com/simobr/photosweep/debug/DebugPileTools.kt")
            assertTrue("$variant DebugPileTools.kt is missing", file.isFile)
            val code = file.code()
            assertTrue("$variant: no `object DebugPileTools`", "object DebugPileTools" in code)
            assertTrue("$variant: no scopePhotos", "fun scopePhotos(" in code)
            assertTrue("$variant: no Chips", "fun Chips(" in code)
        }
    }

    @Test
    fun `the debug chip strings live in the debug resource set only`() {
        val main = File(module, "src/main/res/values/strings.xml")
        val debug = File(module, "src/debug/res/values/strings.xml")
        assertTrue("main strings.xml not found", main.isFile)
        assertTrue("debug strings.xml not found", debug.isFile)

        val mainXml = main.xml()
        val debugXml = debug.xml()

        DEBUG_STRING_NAMES.forEach { name ->
            assertTrue("$name must be declared in src/debug/res", "name=\"$name\"" in debugXml)
            assertFalse(
                "$name is declared in src/main/res and would ship in the release APK",
                "name=\"$name\"" in mainXml,
            )
        }
    }

    /**
     * Names are only half of it. A release-set string whose *value* talks about seeding or
     * wiping a test gallery ships those words just as surely as `piles_wipe` would.
     */
    @Test
    fun `no release-set string mentions the test gallery`() {
        val mainXml = File(module, "src/main/res/values/strings.xml").xml()
        val offenders = TEST_GALLERY_PHRASES.filter { it.toRegex(RegexOption.IGNORE_CASE)
            .containsMatchIn(mainXml) }

        assertEquals(
            "these phrases appear in src/main/res/values/strings.xml and would ship",
            emptyList<String>(),
            offenders,
        )
    }

    private companion object {
        /** Every name for the debug-only scope filter, in any spelling it has had. */
        val FILTER_MARKERS = listOf(
            "GallerySeeder",
            "ROOT_RELATIVE_PATH",
            "testGalleryOnly",
            "PhotoSweepTest",
        )

        val DEBUG_STRING_NAMES = listOf(
            "piles_seed",
            "piles_wipe",
            "piles_scope_test",
            "piles_scope_all",
        )

        val TEST_GALLERY_PHRASES = listOf("test gallery", "test photos", "PhotoSweepTest")

        val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("//[^\n]*")
        val XML_COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    }
}
