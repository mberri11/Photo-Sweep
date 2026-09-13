package com.simobr.photosweep.ui.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In-memory stand-in for the DataStore-backed preference.
 *
 * Same reasoning as `FakePendingMarkDao`: the JVM suite has no Android context, and the real
 * `DataStore` needs one. Implementing the interface costs nothing and keeps the dependency list
 * short. What is being tested here is the contract the sweep screen relies on — a default that
 * is available immediately, and a write that is visible to the next read.
 */
class FakeHapticsPreference(initial: Boolean = HapticsPreference.DEFAULT) : HapticsPreference {
    private val state = MutableStateFlow(initial)

    /** Counts writes, so a test can tell a no-op toggle from a real one. */
    var writes: Int = 0
        private set

    override val enabled: Flow<Boolean> = state

    override suspend fun setEnabled(value: Boolean) {
        writes++
        state.value = value
    }
}

class HapticsPreferenceTest {

    @Test
    fun `the default is on`() {
        assertTrue(
            "haptics default to on: performHapticFeedback already respects the system-wide " +
                "setting, so defaulting off would silence the gesture to solve a solved problem",
            HapticsPreference.DEFAULT,
        )
    }

    @Test
    fun `an untouched preference emits the default immediately`() = runTest {
        val pref = FakeHapticsPreference()
        assertEquals(HapticsPreference.DEFAULT, pref.enabled.first())
        assertEquals("reading must not write", 0, pref.writes)
    }

    @Test
    fun `a write survives a read back`() = runTest {
        val pref = FakeHapticsPreference()

        pref.setEnabled(false)
        assertEquals(false, pref.enabled.first())

        pref.setEnabled(true)
        assertEquals(true, pref.enabled.first())

        assertEquals(2, pref.writes)
    }

    /**
     * The stored key is written to disk, so it survives app updates and cannot be renamed
     * without silently resetting every user's choice.
     */
    @Test
    fun `the stored key is frozen`() {
        assertEquals("haptics_enabled", HapticsPreference.KEY)
    }
}
