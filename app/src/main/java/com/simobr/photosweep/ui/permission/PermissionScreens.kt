package com.simobr.photosweep.ui.permission

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simobr.photosweep.R
import com.simobr.photosweep.ui.theme.PsColor
import com.simobr.photosweep.ui.theme.PsDim
import com.simobr.photosweep.ui.theme.PsType

/**
 * Screen 02 — the pre-permission rationale.
 *
 * Shown before Android's own dialog, because "Allow Photo Sweep to access photos?" on its own
 * tells the user nothing about what the app will do with them.
 *
 * @param canPrompt false once Android has stopped showing the system dialog, in which case
 *   the primary button has to lead to Settings instead of to a prompt that will not appear.
 */
@Composable
fun PermissionRationaleScreen(
    canPrompt: Boolean,
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PsColor.Midnight)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PsDim.screenPadH),
    ) {
        Spacer(Modifier.height(24.dp))

        AppMark()

        Spacer(Modifier.height(28.dp))

        Text(
            text = stringResource(R.string.permission_title),
            style = PsType.screenTitle,
            color = PsColor.Frame,
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.permission_body),
            style = PsType.body,
            color = PsColor.Steel,
        )

        Spacer(Modifier.height(28.dp))

        Promise(
            title = R.string.permission_point_privacy_title,
            body = R.string.permission_point_privacy_body,
        )
        Promise(
            title = R.string.permission_point_analysis_title,
            body = R.string.permission_point_analysis_body,
        )
        Promise(
            title = R.string.permission_point_consent_title,
            body = R.string.permission_point_consent_body,
        )

        Spacer(Modifier.height(28.dp))

        PrimaryButton(
            label = stringResource(
                if (canPrompt) R.string.permission_allow else R.string.permission_open_settings,
            ),
            onClick = onAllow,
        )

        Spacer(Modifier.height(4.dp))

        TextButton(
            onClick = onNotNow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.permission_not_now),
                style = PsType.buttonLabel,
                color = PsColor.Steel,
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(
                if (canPrompt) R.string.permission_footer else R.string.permission_footer_settings,
            ),
            style = PsType.photoMeta,
            color = PsColor.SteelDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Android 14+ partial access.
 *
 * Deliberately a dead end rather than a degraded home screen. Building piles out of a
 * hand-picked dozen photos would produce a screen that is technically working and completely
 * useless, and the user would have no way to tell which of those it was.
 *
 * @param visibleCount how many photos the app can actually see, or null while counting.
 */
@Composable
fun PartialAccessScreen(
    visibleCount: Int?,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PsColor.Midnight)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PsDim.screenPadH),
        verticalArrangement = Arrangement.Center,
    ) {
        AppMark()

        Spacer(Modifier.height(28.dp))

        Text(
            text = if (visibleCount == null) {
                stringResource(R.string.permission_partial_heading)
            } else {
                pluralStringResource(
                    R.plurals.permission_partial_title,
                    visibleCount,
                    visibleCount,
                )
            },
            style = PsType.screenTitle,
            color = PsColor.Frame,
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.permission_partial_body),
            style = PsType.body,
            color = PsColor.Steel,
        )

        Spacer(Modifier.height(28.dp))

        PrimaryButton(
            label = stringResource(R.string.permission_partial_action),
            onClick = onOpenSettings,
        )

        Spacer(Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.permission_partial_footer),
            style = PsType.photoMeta,
            color = PsColor.SteelDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AppMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PsColor.Panel),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(52.dp),
        )
    }
}

@Composable
private fun Promise(title: Int, body: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = PsColor.SteelDim.copy(alpha = 0.35f))
        Row(modifier = Modifier.padding(top = 18.dp, bottom = 18.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(PsColor.Frame),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = stringResource(title),
                    style = PsType.pileName,
                    color = PsColor.Frame,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(body),
                    style = PsType.body,
                    color = PsColor.Steel,
                )
            }
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PsColor.Frame,
            contentColor = PsColor.Midnight,
        ),
    ) {
        Text(text = label, style = PsType.buttonLabel)
    }
}
