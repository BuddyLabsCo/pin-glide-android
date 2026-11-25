package com.buddylabs.pinglide.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.PairingHost
import com.buddylabs.pinglide.ui.components.RingedEmblem
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
internal fun PairingExpiredScreen(host: PairingHost, onRescan: () -> Unit, onLeave: () -> Unit) {
    val title = stringResource(R.string.pairing_expired_title)
    val bottomInset = with(LocalDensity.current) { WindowInsets.safeDrawing.getBottom(this).toDp() }
    ScreenLayout {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(top = 8.dp, bottom = (22.dp - bottomInset).coerceAtLeast(0.dp))
                    .semantics { paneTitle = title }
            ) {
                Spacer(Modifier.weight(1f))
                ExpiredSessionMessage(title)
                Spacer(Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRescan,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_qr), null, Modifier.size(22.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(
                            stringResource(R.string.pairing_rescan),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.14.sp
                        )
                    }
                    TextButton(
                        onClick = onLeave,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(
                            stringResource(
                                if (host == PairingHost.Onboarding) {
                                    R.string.pairing_later
                                } else {
                                    R.string.pairing_expired_return
                                }
                            ),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpiredSessionMessage(title: String) {
    val warning = colorResource(R.color.setup_on_warning)
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { titleFocus.requestFocus() }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 53.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RingedEmblem(
            RoundedCornerShape(44.dp),
            containerColor = colorResource(R.color.setup_warning_container)
        ) {
            Icon(painterResource(R.drawable.ic_clock), null, Modifier.size(54.dp), tint = warning)
        }
        Row(
            Modifier.padding(top = 21.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.size(18.dp, 2.dp).background(warning, CircleShape))
            Text(
                stringResource(R.string.pairing_expired_eyebrow),
                color = warning,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight(800),
                letterSpacing = 1.21.sp
            )
        }
        Text(
            title,
            modifier = Modifier.focusRequester(titleFocus).focusable().semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
            style = MaterialTheme.typography.headlineSmall.copy(
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight(750),
                letterSpacing = (-1.17).sp,
                textAlign = TextAlign.Center
            )
        )
        Text(
            stringResource(R.string.pairing_expired_instruction),
            modifier = Modifier.padding(top = 4.dp).widthIn(max = 306.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 18.sp,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Center
            )
        )
    }
}
