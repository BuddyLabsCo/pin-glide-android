package com.buddylabs.pinglide.ui.pairing

import androidx.compose.foundation.focusable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle

@Composable
internal fun PairingAnnouncedHeading(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { titleFocus.requestFocus() }
    Text(
        text,
        modifier = modifier.focusRequester(titleFocus).focusable().semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        },
        style = style
    )
}
