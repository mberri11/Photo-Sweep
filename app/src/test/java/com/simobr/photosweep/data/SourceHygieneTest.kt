package com.simobr.photosweep.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Two invariants that are architectural rather than behavioural, and so cannot be caught by
 * testing outputs — only by looking at the source.
 *
 * Both are the kind of rule that holds perfectly until the day somebody in a hurry adds one
 * more call site, which is exactly when it stops being caught by review.
 */
class SourceHygieneTest {

    private val mainSources: List<File> by lazy {
        val candidates = listOf(File("src/main/java"), File("app/src/main/java"))
        val root = candidates.firstOrNull { it.isDirectory }
        requireNotNull(root) {
            "could not locate main sources from ${File("").absolutePath}"
        }
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /**
     * Strips comments before matching. These rules are about what the code *does*; a scan
     * that fires on the KDoc explaining the rule is a scan that gets deleted.
     */
    private fun File.code(): String = readText()
        .replace(BLOCK_COMMENT, "")
        .replace(LINE_COMMENT, "")

    @Test
    fun `the source tree was actually found`() {
        assertTrue("no Kotlin sources found to scan", mainSources.size >= 5)
    }

    /**
     * MediaStore's three date columns are read in exactly two files: the repository that
     * pulls them out of the cursor, and the model that folds them into one timestamp. Any
     * third reader is a second opinion about which column wins and what unit it is in, which
     * is how a gallery ends up half-dated in 1970.
     */
    @Test
    fun `raw date columns are read in only the repository and the model`() {
        val allowed = setOf("MediaRepository.kt", "Photo.kt")
        val markers = listOf("rawDateTakenMs", "rawDateAddedSec", "rawDateModifiedSec")

        val offenders = mainSources
            .filter { it.name !in allowed }
            .filter { file -> markers.any { it in file.code() } }
            .map { it.name }

        assertEquals(
            "these files read MediaStore's raw date columns directly; " +
                "use Photo.capturedAtMs instead",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * The data layer never touches pixels. Decoding belongs to Coil, at the size the screen
     * asked for, at the moment the screen asks — not to a repository walking a cursor.
     */
    @Test
    fun `the data layer references no bitmap or drawable types`() {
        val dataLayer = mainSources.filter { it.path.contains("/data/") }
        assertTrue("no data-layer sources found", dataLayer.size >= 4)

        val offenders = dataLayer
            .filter { file ->
                val text = file.code()
                "Bitmap" in text || "Drawable" in text
            }
            .map { it.name }

        assertEquals(
            "the data layer must stay metadata-only",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * The app never mutates MediaStore through the content resolver.
     *
     * Every change to a photo — trashing, restoring, deleting — goes through a
     * `MediaStore.create*Request` IntentSender that **the system** executes after showing
     * the user a dialog it drew itself. That is what makes the consent real rather than a
     * checkbox the app draws and then ignores.
     *
     * The debug seeder is the exception: it writes and deletes its own synthetic files, and
     * is excluded by path.
     *
     * Room writes are not MediaStore writes and are deliberately not matched here — the
     * receiver has to be a resolver.
     */
    @Test
    fun `nothing mutates MediaStore through the content resolver`() {
        val offenders = mainSources
            .filter { !it.path.contains("/debug/") }
            .filter { RESOLVER_MUTATION.containsMatchIn(it.code()) }
            .map { it.name }

        assertEquals(
            "MediaStore must only be changed by a system-confirmed IntentSender request",
            emptyList<String>(),
            offenders,
        )
    }

    /**
     * The permanent-delete request is the one call in the app with no undo behind it.
     */
    @Test
    fun `the permanent delete request only exists in a dangerously-named function`() {
        val builders = mainSources.filter { "createDeleteRequest" in it.code() }
        assertTrue("expected the delete request builder to exist by now", builders.isNotEmpty())

        builders.forEach { file ->
            assertTrue(
                "${file.name} builds a permanent delete request but declares no " +
                    "dangerously-named function",
                "fun dangerously" in file.code(),
            )
        }
    }

    /** Anything that can destroy user data announces itself in its name. */
    @Test
    fun `every permanent delete lives in a dangerously-named function`() {
        val deleters = mainSources.filter { ".delete(" in it.code() }
        assertTrue("expected the seeder's wipe to be found", deleters.isNotEmpty())

        deleters.forEach { file ->
            val text = file.code()
            assertTrue(
                "${file.name} deletes but declares no dangerously-named function",
                "fun dangerously" in text,
            )
        }
    }

    private companion object {
        /** A resolver mutation: `resolver.delete(`, `contentResolver.insert(`, and friends. */
        val RESOLVER_MUTATION = Regex("""[Rr]esolver\s*\.\s*(delete|insert|update)\s*\(""")
        val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("//[^\n]*")
    }
}
