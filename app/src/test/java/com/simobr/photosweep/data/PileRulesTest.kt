package com.simobr.photosweep.data

import com.simobr.photosweep.data.piles.PileCategory
import com.simobr.photosweep.data.piles.PileRules
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Classification across 40 synthetic folder shapes.
 *
 * The cases that matter most are the localised ones. A French phone calls the screenshot
 * folder "Captures d'écran" and an Arabic phone calls it "لقطات الشاشة"; if the rules only
 * knew the English name, the Screenshots pile would be empty for most of the planet and the
 * app would look broken in a way no English-speaking tester would ever see.
 *
 * The near-miss cases matter almost as much: "Screenshots Backup" and "Download Manager
 * Extras" must *not* be claimed, or an over-eager `contains` would quietly hand a user's
 * backup folder to a sweeping screen.
 */
class PileRulesTest {

    private data class Case(
        val bucket: String?,
        val path: String?,
        val expected: PileCategory,
        val note: String,
    )

    private val cases = listOf(
        // -- Screenshots, English ---------------------------------------------------------
        Case("Screenshots", "DCIM/Screenshots/", PileCategory.Screenshots, "stock DCIM"),
        Case("Screenshots", "Pictures/Screenshots/", PileCategory.Screenshots, "stock Pictures"),
        Case("Screenshot", "Pictures/Screenshot/", PileCategory.Screenshots, "singular vendor"),
        Case("Screenshoots", "Pictures/Screenshoots/", PileCategory.Screenshots, "vendor typo"),
        Case("SCREENSHOTS", "PICTURES/SCREENSHOTS/", PileCategory.Screenshots, "upper case"),
        Case(null, "DCIM/Screenshots/", PileCategory.Screenshots, "path only, null bucket"),
        Case("Bildschirmfotos", "DCIM/Screenshots/", PileCategory.Screenshots, "unknown locale, path saves it"),

        // -- Screenshots, localised bucket names ------------------------------------------
        Case("Captures d'écran", "Pictures/Captures d'écran/", PileCategory.Screenshots, "fr, ascii apostrophe"),
        Case("Captures d’écran", "Pictures/Captures d’écran/", PileCategory.Screenshots, "fr, typographic apostrophe U+2019"),
        Case("Captures d'ecran", "Pictures/Captures d'ecran/", PileCategory.Screenshots, "fr, accents dropped by vendor"),
        Case("Capture d'écran", "Pictures/Capture d'écran/", PileCategory.Screenshots, "fr singular"),
        Case("لقطات الشاشة", "Pictures/لقطات الشاشة/", PileCategory.Screenshots, "ar plural"),
        Case("لقطة شاشة", "Pictures/لقطة شاشة/", PileCategory.Screenshots, "ar singular"),

        // -- WhatsApp ---------------------------------------------------------------------
        Case("WhatsApp Images", "Pictures/WhatsApp/Media/WhatsApp Images/", PileCategory.WhatsApp, "current layout"),
        Case("WA Images", "Android/media/com.whatsapp/WhatsApp/Media/WA Images/", PileCategory.WhatsApp, "scoped-storage layout"),
        Case(null, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/", PileCategory.WhatsApp, "path only"),
        Case("WhatsApp Images", null, PileCategory.WhatsApp, "bucket only, null path"),
        Case("WhatsApp Business Images", "Pictures/WhatsApp Business/Media/", PileCategory.WhatsApp, "business client"),
        Case("صور WhatsApp", "Pictures/WhatsApp/Media/WhatsApp Images/", PileCategory.WhatsApp, "ar bucket, English path saves it"),
        Case("whatsapp images", "pictures/whatsapp/media/whatsapp images/", PileCategory.WhatsApp, "all lower case"),
        Case("WhatsApp Animated Gifs", "Pictures/WhatsApp/Media/WhatsApp Animated Gifs/", PileCategory.WhatsApp, "sibling media folder"),

        // -- Downloads ---------------------------------------------------------------------
        Case("Download", "Download/", PileCategory.Downloads, "stock singular"),
        Case("Downloads", "Download/", PileCategory.Downloads, "stock plural bucket"),
        Case(null, "Download/Telegram/", PileCategory.Downloads, "path prefix, nested"),
        Case("Téléchargements", "Download/", PileCategory.Downloads, "fr bucket, English path"),
        Case("Téléchargements", "Pictures/Téléchargements/", PileCategory.Downloads, "fr bucket only"),
        Case("Téléchargement", "Pictures/Téléchargement/", PileCategory.Downloads, "fr singular"),
        Case("Telechargements", "Pictures/Telechargements/", PileCategory.Downloads, "fr, accents dropped"),
        Case("التنزيلات", "Pictures/التنزيلات/", PileCategory.Downloads, "ar downloads"),
        Case("التحميلات", "Pictures/التحميلات/", PileCategory.Downloads, "ar downloads, alternate"),
        Case("التنزيل", "Pictures/التنزيل/", PileCategory.Downloads, "ar singular"),

        // -- Precedence ---------------------------------------------------------------------
        Case("Screenshots", "Download/Screenshots/", PileCategory.Screenshots, "screenshot inside Download wins for Screenshots"),

        // -- Everything else: month piles ----------------------------------------------------
        Case("Camera", "DCIM/Camera/", PileCategory.Other, "the camera roll"),
        Case("Appareil photo", "DCIM/Camera/", PileCategory.Other, "fr camera"),
        Case("صور الكاميرا", "DCIM/Camera/", PileCategory.Other, "ar camera"),
        Case("Instagram", "Pictures/Instagram/", PileCategory.Other, "third-party app folder"),
        Case(null, null, PileCategory.Other, "both null"),
        Case("", "", PileCategory.Other, "both empty"),

        // -- Near misses that must NOT be claimed --------------------------------------------
        Case("Screenshots Backup", "Pictures/Screenshots Backup/", PileCategory.Other, "must not match 'Screenshots/'"),
        Case("Download Manager Extras", "Pictures/Download Manager Extras/", PileCategory.Other, "must not match 'Download/'"),
    )

    @Test
    fun `the case table covers forty folder shapes`() {
        assertEquals(40, cases.size)
    }

    @Test
    fun `every synthetic folder classifies as expected`() {
        cases.forEach { case ->
            val actual = PileRules.classify(case.bucket, case.path)
            assertEquals(
                "bucket=${case.bucket} path=${case.path} (${case.note})",
                case.expected,
                actual,
            )
        }
    }

    @Test
    fun `localised buckets are covered for both french and arabic`() {
        val localised = cases.filter { bucket ->
            bucket.bucket?.any { it in '؀'..'ۿ' || it in "éèêàçÉÈ’" } == true
        }
        // Guards the guard: if someone deletes the localised rows, this fails rather than
        // leaving a test that passes because it no longer checks anything.
        assertEquals(true, localised.count { it.expected == PileCategory.Screenshots } >= 4)
        assertEquals(true, localised.count { it.expected == PileCategory.Downloads } >= 4)
    }

    @Test
    fun `normalise folds case accents and apostrophes onto one string`() {
        assertEquals(PileRules.normalise("Captures d’écran"), PileRules.normalise("captures d'ecran"))
        assertEquals(PileRules.normalise("TÉLÉCHARGEMENTS"), PileRules.normalise("telechargements"))
        assertEquals("", PileRules.normalise(null))
    }
}
