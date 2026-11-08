package com.buddylabs.pinglide.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
fun WelcomeScreen(onStartSetup: () -> Unit, onPrivacy: () -> Unit) {
    val title = stringResource(R.string.app_name)
    ScreenLayout {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).semantics {
                paneTitle =
                    title
            }
                .padding(top = 30.dp, bottom = 24.dp)
        ) {
            Brand()
            WelcomePushVisual()
            Column(
                modifier = Modifier.padding(top = 26.dp).semantics(mergeDescendants = true) {
                    heading()
                }
            ) {
                Text(
                    stringResource(R.string.welcome_headline),
                    fontSize = 43.sp,
                    lineHeight = 46.sp,
                    letterSpacing = (-1).sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    stringResource(R.string.welcome_headline_accent),
                    fontSize = 43.sp,
                    lineHeight = 46.sp,
                    letterSpacing = (-1).sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                stringResource(R.string.welcome_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
            TextButton(onClick = onPrivacy, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.ic_shield), contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.privacy_title),
                    modifier = Modifier.weight(1f, fill = false),
                    textDecoration = TextDecoration.Underline
                )
                Icon(painterResource(R.drawable.ic_chevron), contentDescription = null)
            }
        }
        Button(
            onClick = onStartSetup,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).heightIn(min = 56.dp)
        ) {
            Text(stringResource(R.string.start_setup))
            Spacer(Modifier.size(8.dp))
            Icon(painterResource(R.drawable.ic_chevron), contentDescription = null)
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
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
        )
        Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.brand_wordmark),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
