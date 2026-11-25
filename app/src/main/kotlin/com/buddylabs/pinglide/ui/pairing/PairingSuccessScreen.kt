package com.buddylabs.pinglide.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.ui.components.PairingTestAction
import com.buddylabs.pinglide.ui.components.RingedEmblem
import com.buddylabs.pinglide.ui.components.ScreenLayout
import com.buddylabs.pinglide.ui.components.TestMessageAction

@Composable
internal fun PairingSuccessScreen(action: TestMessageAction, onHome: () -> Unit) {
    val title = stringResource(R.string.pairing_success)
    ScreenLayout {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val compact = maxHeight < 360.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
            val scroll = Modifier.verticalScroll(rememberScrollState())
            Column(
                Modifier.fillMaxSize().then(if (compact) scroll else Modifier)
                    .semantics { paneTitle = title }
            ) {
                BoxWithConstraints(
                    (if (compact) Modifier else Modifier.weight(1f)).fillMaxWidth()
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .then(if (compact) Modifier else scroll.heightIn(min = maxHeight))
                            .padding(top = 12.dp, bottom = 38.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        RingedEmblem(
                            RoundedCornerShape(44.dp),
                            containerColor = colorResource(R.color.pairing_success_container)
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_check),
                                null,
                                Modifier.size(54.dp),
                                tint = colorResource(R.color.home_online)
                            )
                        }
                        PairingAnnouncedHeading(
                            title,
                            modifier = Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 32.sp,
                                lineHeight = 35.sp,
                                fontWeight = FontWeight(760),
                                letterSpacing = (-1.76).sp,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PairingTestAction(action)
                    TextButton(
                        onClick = onHome,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.pairing_home), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
