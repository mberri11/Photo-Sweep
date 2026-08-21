package com.simobr.photosweep.data

import com.simobr.photosweep.data.permission.MediaAccess
import com.simobr.photosweep.data.permission.MediaPermission
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every combination of API level and grant state.
 *
 * The bug this is built to prevent is reporting partial access as if it were full. The app
 * would then build piles from a hand-picked dozen photos, show a nearly empty home screen,
 * and give the user nothing to explain it. The inverse — reporting a full grant as partial —
 * would block a perfectly permitted user out of their own gallery. Both are silent.
 */
class PermissionResolutionTest {

    private companion object {
        const val ANDROID_11 = 30
        const val ANDROID_12L = 32
        const val ANDROID_13 = 33
        const val ANDROID_14 = 34
        const val ANDROID_16 = 36

        val ALL_LEVELS = listOf(ANDROID_11, ANDROID_12L, ANDROID_13, ANDROID_14, ANDROID_16)
    }

    private fun resolve(sdk: Int, full: Boolean, userSelected: Boolean) =
        MediaPermission.resolve(sdk, full, userSelected)

    @Test
    fun `a full grant is full access on every supported api level`() {
        ALL_LEVELS.forEach { sdk ->
            assertEquals("api $sdk", MediaAccess.Full, resolve(sdk, full = true, userSelected = false))
            assertEquals("api $sdk", MediaAccess.Full, resolve(sdk, full = true, userSelected = true))
        }
    }

    @Test
    fun `no grant at all is no access on every supported api level`() {
        ALL_LEVELS.forEach { sdk ->
            assertEquals("api $sdk", MediaAccess.None, resolve(sdk, full = false, userSelected = false))
        }
    }

    @Test
    fun `selected-photos only is partial access from api 34`() {
        assertEquals(MediaAccess.PartialSelectionOnly, resolve(ANDROID_14, full = false, userSelected = true))
        assertEquals(MediaAccess.PartialSelectionOnly, resolve(ANDROID_16, full = false, userSelected = true))
    }

    @Test
    fun `partial access does not exist below api 34`() {
        // The user-selected permission is not a platform concept before Android 14. If it
        // somehow reads as granted there, it must not produce a partial verdict — that would
        // strand an Android 11 user on a screen describing a feature their OS does not have.
        listOf(ANDROID_11, ANDROID_12L, ANDROID_13).forEach { sdk ->
            assertEquals("api $sdk", MediaAccess.None, resolve(sdk, full = false, userSelected = true))
        }
    }

    @Test
    fun `a full grant is never reported as partial when both permissions are held`() {
        // API 34+ grants the user-selected permission alongside a full grant. Checking
        // partial first would report every fully permitted user as partial.
        assertEquals(MediaAccess.Full, resolve(ANDROID_16, full = true, userSelected = true))
    }

    @Test
    fun `the full matrix has no unreachable or surprising cells`() {
        val expected = mutableMapOf<Triple<Int, Boolean, Boolean>, MediaAccess>()
        ALL_LEVELS.forEach { sdk ->
            listOf(true, false).forEach { full ->
                listOf(true, false).forEach { userSelected ->
                    expected[Triple(sdk, full, userSelected)] = when {
                        full -> MediaAccess.Full
                        userSelected && sdk >= ANDROID_14 -> MediaAccess.PartialSelectionOnly
                        else -> MediaAccess.None
                    }
                }
            }
        }
        assertEquals(20, expected.size)
        expected.forEach { (key, want) ->
            val (sdk, full, userSelected) = key
            assertEquals("api=$sdk full=$full userSelected=$userSelected", want, resolve(sdk, full, userSelected))
        }
    }
}
