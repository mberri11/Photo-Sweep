package com.simobr.photosweep.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simobr.photosweep.BuildConfig
import com.simobr.photosweep.R
import com.simobr.photosweep.ui.theme.PsColor
import com.simobr.photosweep.ui.theme.PsDim
import com.simobr.photosweep.ui.theme.PsType
import kotlinx.coroutines.launch

private val RowShape = RoundedCornerShape(14.dp)

/**
 * Screen 13. Five sections, and deliberately no sixth.
 *
 * What is **not** here is as much of the design as what is:
 *
 *  - **No theme picker.** The app is one dark scheme, because the UI is a frame around the
 *    user's photos and a tinted frame changes how their photos read.
 *  - **No retention picker.** The 30 days belong to Android and are not configurable. Offering
 *    "7 / 30 / 60 days" would be a control that does nothing — see RELEASE.md section 6. The
 *    row is a static line instead.
 *  - **No "rate us", no "share app", no social links.** Not in 1.0.
 */
@Composable
fun SettingsScreen(
    onOpenTrash: () -> Unit,
    onOpenLicences: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val haptics = remember(context) { DataStoreHapticsPreference(context) }
    val hapticsEnabled by haptics.enabled
        .collectAsStateWithLifecycle(initialValue = HapticsPreference.DEFAULT)

    /**
     * The app-details screen is not guaranteed to exist. A handful of ROMs ship without it,
     * and `startActivity` on an unresolvable intent is an ActivityNotFoundException in the
     * middle of the privacy section. Resolved once, here, and the row is hidden if it is
     * absent rather than offered and then crashing.
     */
    val appDetails = remember(context) {
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ).takeIf { it.resolveActivity(context.packageManager) != null }
    }

    /**
     * The policy link is **not** guarded by `resolveActivity`, and that is deliberate.
     *
     * On API 30+ package-visibility filtering makes `resolveActivity` return null for a browser
     * unless the manifest declares a matching `<queries>` element — so guarding this the way
     * the permissions row is guarded would hide the row on every device, and a privacy policy
     * the user cannot reach is a Play listing problem as well as a bad answer. The row stays,
     * and the launch fails softly on the vanishingly rare device with no browser at all.
     */
    val privacyPolicy = remember { Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PsDim.screenPadH),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings_title),
            style = PsType.screenTitle,
            color = PsColor.Frame,
        )

        // ---- TRASH -------------------------------------------------------------------------
        Section(R.string.settings_trash_caption)
        StaticLine(stringResource(R.string.settings_trash_line))
        Spacer(Modifier.height(10.dp))
        SettingsRow(title = stringResource(R.string.settings_open_trash), onClick = onOpenTrash)

        // ---- SWEEPING ----------------------------------------------------------------------
        Section(R.string.settings_sweeping_caption)
        ToggleRow(
            title = stringResource(R.string.settings_haptics),
            subtitle = stringResource(R.string.settings_haptics_sub),
            checked = hapticsEnabled,
            onToggle = { scope.launch { haptics.setEnabled(!hapticsEnabled) } },
        )

        // ---- PRIVACY -----------------------------------------------------------------------
        Section(R.string.settings_privacy_caption)
        StaticLine(stringResource(R.string.settings_privacy_body))
        Spacer(Modifier.height(10.dp))
        if (appDetails != null) {
            SettingsRow(
                title = stringResource(R.string.settings_permissions_row),
                subtitle = stringResource(R.string.settings_permissions_row_subtitle),
                onClick = { context.startActivity(appDetails) },
            )
            Spacer(Modifier.height(10.dp))
        }
        SettingsRow(
            title = stringResource(R.string.settings_privacy_policy),
            onClick = { runCatching { context.startActivity(privacyPolicy) } },
        )

        // ---- ABOUT -------------------------------------------------------------------------
        Section(R.string.settings_about_caption)
        SettingsRow(
            title = stringResource(R.string.settings_version_row),
            trailing = stringResource(
                R.string.settings_version_value,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
            ),
            onClick = null,
        )
        Spacer(Modifier.height(10.dp))
        SettingsRow(
            title = stringResource(R.string.settings_licences),
            onClick = onOpenLicences,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(captionRes: Int) {
    Spacer(Modifier.height(22.dp))
    // sectionCaption's 0.24em tracking is drawn for capitals, so the call site uppercases.
    Text(
        text = stringResource(captionRes).uppercase(),
        style = PsType.sectionCaption,
        color = PsColor.Steel,
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun StaticLine(text: String) {
    Text(text = text, style = PsType.body, color = PsColor.Steel)
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(PsColor.Panel)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = PsType.pileName, color = PsColor.Frame)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(text = subtitle, style = PsType.pileMeta, color = PsColor.Steel)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Text(text = trailing, style = PsType.pileMeta, color = PsColor.Steel)
        }
    }
}

/**
 * The toggle.
 *
 * On-state is `Frame`, not `Keep`. Green means "keep this photo" in the only gesture this app
 * has, and a settings switch is not a gesture — the same rule the bottom nav follows.
 */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(PsColor.Panel)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = PsType.pileName, color = PsColor.Frame)
            Spacer(Modifier.height(3.dp))
            Text(text = subtitle, style = PsType.pileMeta, color = PsColor.Steel)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked)
    }
}

/** A track and a knob. Material's Switch would bring its own colour scheme; this one does not. */
@Composable
private fun Switch(checked: Boolean) {
    val trackShape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .width(46.dp)
            .height(26.dp)
            .clip(trackShape)
            .background(if (checked) PsColor.Frame else PsColor.SteelDim)
            .border(1.dp, if (checked) PsColor.Frame else PsColor.Steel, trackShape),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(50))
                .background(if (checked) PsColor.Midnight else PsColor.Frame),
        )
    }
}

/**
 * Screen reached from About.
 *
 * The Manrope OFL text has been committed in `assets/licenses/` since the fonts landed, with
 * nothing in the app displaying it. The SIL Open Font License requires the notice to travel
 * with the font, so this screen is the compliance fix, not a nicety.
 */
@Composable
fun LicencesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val ofl = remember(context) {
        runCatching {
            context.assets.open("licenses/manrope_ofl.txt").bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PsDim.screenPadH),
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings_licences),
            style = PsType.screenTitle,
            color = PsColor.Frame,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.licences_notices),
            style = PsType.body,
            color = PsColor.Steel,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = ofl ?: stringResource(R.string.licences_ofl_missing),
            style = PsType.photoMeta,
            color = PsColor.Steel,
        )
        Spacer(Modifier.height(32.dp))
    }
}
