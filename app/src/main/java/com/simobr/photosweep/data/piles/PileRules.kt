package com.simobr.photosweep.data.piles

import java.text.Normalizer
import java.util.Locale

/** Which rule claimed a photo, before month grouping is applied. */
enum class PileCategory {
    Screenshots,
    WhatsApp,
    Downloads,

    /** Claimed by nothing — becomes a month pile. */
    Other,
}

/**
 * The single place where folder names and paths are matched.
 *
 * Two facts drive the shape of this object:
 *
 *  1. `BUCKET_DISPLAY_NAME` is the *localised* folder name. On a French phone the screenshot
 *     folder is "Captures d'écran"; on an Arabic one it is "لقطات الشاشة". Matching only on
 *     the English string means the Screenshots pile is silently empty for most of the world.
 *  2. `RELATIVE_PATH` is the on-disk path and stays English — "DCIM/Screenshots/" regardless
 *     of locale — but it changes between app versions, and WhatsApp in particular has moved
 *     its media directory more than once.
 *
 * Neither signal is reliable alone, so every rule matches on both, and every rule lives here
 * so that adding a locale or a vendor folder is one edit to one file.
 *
 * Precedence is Screenshots, then WhatsApp, then Downloads, then month piles. It only
 * matters for paths that satisfy two rules at once (a screenshot saved into Download/, say),
 * which is arbitrary either way — but it is fixed and tested rather than incidental.
 */
object PileRules {

    private val COMBINING_MARKS = Regex("\\p{Mn}+")

    /**
     * Folds a MediaStore string into a comparable form:
     *
     *  - lower-cased in [Locale.ROOT];
     *  - the typographic apostrophe (U+2019) unified to the ASCII one, because Android's
     *    French resources have used both and the two look identical to a user;
     *  - combining marks stripped, so "Téléchargements" and a vendor ROM's unaccented
     *    "Telechargements" fold to one string and only need one entry below.
     *
     * Arabic folder names pass through unchanged apart from case, which Arabic does not have.
     */
    fun normalise(value: String?): String {
        val lowered = value.orEmpty().trim().lowercase(Locale.ROOT).replace('’', '\'')
        return Normalizer.normalize(lowered, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
    }

    /**
     * Localised names for the system screenshot folder. Written as they appear on a device;
     * folded through [normalise] here so the literals stay readable and nobody has to
     * remember to pre-lower-case a new entry.
     */
    val screenshotBuckets: Set<String> = foldAll(
        "Screenshots",
        "Screenshot",
        "Captures d'écran",     // fr
        "Capture d'écran",      // fr, singular
        "لقطات الشاشة",          // ar
        "لقطة شاشة",             // ar, singular
        "Screenshoots",         // seen on some vendor ROMs
    )

    /** Path fragments that identify a screenshot folder anywhere in the tree. */
    val screenshotPathFragments: List<String> = foldAll("Screenshots/").toList()

    /** Folder names WhatsApp has used for saved images. English on every locale. */
    val whatsAppBuckets: Set<String> = foldAll(
        "WhatsApp Images",
        "WA Images",
        "WhatsApp Business Images",
    )

    /** Path fragments for WhatsApp's media tree, across its several relocations. */
    val whatsAppPathFragments: List<String> = foldAll("WhatsApp/Media/").toList()

    /** Localised names for the system download folder. */
    val downloadBuckets: Set<String> = foldAll(
        "Download",
        "Downloads",
        "Téléchargement",       // fr
        "Téléchargements",      // fr
        "التنزيلات",             // ar
        "التنزيل",               // ar
        "التحميلات",             // ar
    )

    /**
     * Path prefixes for the download folder. A prefix, not a fragment: "Download/" at the
     * root is the system download directory, while "Pictures/Download/" is some app's own
     * subfolder — that one is claimed by the bucket rule instead.
     */
    val downloadPathPrefixes: List<String> = foldAll("Download/").toList()

    /**
     * Classifies a photo by its folder name and path. Both signals are consulted for every
     * rule; either one matching is enough.
     */
    fun classify(bucketDisplayName: String?, relativePath: String?): PileCategory {
        val bucket = normalise(bucketDisplayName)
        val path = normalise(relativePath)

        return when {
            bucket in screenshotBuckets ||
                screenshotPathFragments.any { path.contains(it) } -> PileCategory.Screenshots

            bucket in whatsAppBuckets ||
                whatsAppPathFragments.any { path.contains(it) } -> PileCategory.WhatsApp

            bucket in downloadBuckets ||
                downloadPathPrefixes.any { path.startsWith(it) } -> PileCategory.Downloads

            else -> PileCategory.Other
        }
    }

    private fun foldAll(vararg values: String): Set<String> =
        values.mapTo(LinkedHashSet()) { normalise(it) }
}
