package com.buddylabs.pinglide.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.LogoutState
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
fun WelcomeScreen(
    onStartSetup: () -> Unit,
    onPrivacy: () -> Unit,
    logoutState: LogoutState,
    onRetryLogout: () -> Unit
) {
    val title = stringResource(R.string.app_name)
    ScreenLayout {
        Column(
            modifier = Modifier.weight(1f).windowInsetsPadding(WindowInsets(top = 32.dp))
                .verticalScroll(rememberScrollState())
                .semantics { paneTitle = title }
                .padding(top = 30.dp, bottom = 24.dp)
        ) {
            Brand()
            Column(
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp)
            ) {
                WelcomePushVisual()
                val headline = stringResource(R.string.welcome_headline)
                val accent = stringResource(R.string.welcome_headline_accent)
                val primary = MaterialTheme.colorScheme.primary
                val language = LocalConfiguration.current.locales[0].language
                Text(
                    buildAnnotatedString {
                        appendLine(headline)
                        withStyle(SpanStyle(color = primary)) { append(accent) }
                    },
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 43.sp,
                        lineHeight = 45.sp,
                        letterSpacing = if (language == "zh") (-0.065).em else 0.em,
                        fontWeight = FontWeight(780),
                        lineHeightStyle = LineHeightStyle(
                            LineHeightStyle.Alignment.Center,
                            LineHeightStyle.Trim.None
                        )
                    ),
                    modifier = Modifier.padding(top = 18.dp).semantics { heading() }
                )
                Text(
                    stringResource(R.string.welcome_description),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 22.sp,
                        letterSpacing = 0.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                TextButton(
                    onClick = onPrivacy,
                    modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_shield), null, Modifier.size(16.dp))
                    Spacer(Modifier.size(7.dp))
                    Text(
                        stringResource(R.string.privacy_title),
                        modifier = Modifier.weight(1f, fill = false),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                        textDecoration = TextDecoration.Underline
                    )
                    Spacer(Modifier.size(7.dp))
                    Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(15.dp))
                }
            }
        }
        if (logoutState == LogoutState.Failed) {
            Text(
                stringResource(R.string.account_logout_storage_error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
            TextButton(onClick = onRetryLogout, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.setup_check_again))
            }
        }
        Button(
            onClick = onStartSetup,
            enabled = logoutState == LogoutState.Ready,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)
        ) {
            Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(16.dp))
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.start_setup),
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.14.sp
            )
        }
    }
}

@Composable
private fun Brand() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Image(
            painterResource(R.mipmap.ic_launcher),
            contentDescription = null,
            modifier = Modifier.align(Alignment.Top).size(38.dp).clip(RoundedCornerShape(11.dp))
        )
        Column {
            Text(
                stringResource(R.string.app_name),
                fontSize = 19.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight(740),
                letterSpacing = (-0.67).sp
            )
            Text(
                stringResource(R.string.brand_wordmark),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight(650),
                letterSpacing = 0.88.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
