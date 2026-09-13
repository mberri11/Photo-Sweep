package com.simobr.photosweep.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Whether the sweep gestures tick. */
interface HapticsPreference {

    /**
     * The current setting, starting with [DEFAULT].
     *
     * A `Flow` rather than a suspend getter because the sweep screen reads this during
     * composition and must not wait on disk to draw a card.
     */
    val enabled: Flow<Boolean>

    suspend fun setEnabled(value: Boolean)

    companion object {
        /**
         * On.
         *
         * Haptics go through `View.performHapticFeedback`, which already respects the user's
         * system-wide haptics setting — so a user who has turned feedback off at the OS level
         * feels nothing regardless of this. Defaulting to off would silence the gesture for
         * everyone else to solve a problem the platform has already solved.
         */
        const val DEFAULT = true

        /** The stored key. Written to disk, so it is frozen. */
        const val KEY = "haptics_enabled"
    }
}

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * DataStore Preferences, the only persistence this setting needs.
 *
 * Room would be absurd for one boolean, and `SharedPreferences` would block the main thread on
 * first read. The flow maps a cold or corrupt store onto [HapticsPreference.DEFAULT] rather
 * than throwing: a setting that cannot be read is a setting at its default, not a crash on the
 * one screen the user is mid-gesture on.
 */
class DataStoreHapticsPreference(private val context: Context) : HapticsPreference {

    override val enabled: Flow<Boolean> =
        context.settingsDataStore.data
            // IOException is the documented signal for an unreadable store.
            .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
            .map { it[HAPTICS] ?: HapticsPreference.DEFAULT }

    override suspend fun setEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[HAPTICS] = value }
    }

    private companion object {
        val HAPTICS = booleanPreferencesKey(HapticsPreference.KEY)
    }
}
