package com.buddylabs.pinglide.ui.home

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
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
import com.buddylabs.pinglide.domain.identity.RestorationState
import com.buddylabs.pinglide.ui.components.QqEmblem
import com.buddylabs.pinglide.ui.components.QqLoadingEmblem
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
internal fun HomeLoginScreen(
    state: RestorationState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit
) {
    val loading = state == RestorationState.Loading
    val strings = when (state) {
        RestorationState.Loading -> R.string.home_login_loading to R.string.home_login_loading_copy

        RestorationState.Cancelled ->
            R.string.home_login_cancelled to R.string.home_login_cancelled_copy

        RestorationState.AccountMismatch ->
            R.string.home_login_mismatch to R.string.home_login_mismatch_copy

        else -> R.string.home_login_error to R.string.home_login_error_copy
    }
    val title = stringResource(strings.first)
    val titleFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    LaunchedEffect(state) { titleFocus.requestFocus() }
    ScreenLayout {
        HomeLoginHeader(loading, onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(
                    if (loading) {
                        PaddingValues(
                            top = 12.dp,
                            bottom = 53.dp
                        )
                    } else {
                        PaddingValues(24.dp)
                    }
                ).semantics {
                    paneTitle = title
                    liveRegion = LiveRegionMode.Polite
                },
            verticalArrangement = Arrangement.spacedBy(
                if (loading) 0.dp else 24.dp,
                Alignment.CenterVertically
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (loading) HomeLoginProgressEmblem() else QqEmblem()
            HomeLoginText(title, stringResource(strings.second), titleFocus, loading)
        }
        Column(
            Modifier.fillMaxWidth().imePadding().padding(vertical = if (loading) 0.dp else 16.dp)
        ) {
            if (loading) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(
                        stringResource(R.string.setup_cancel),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.14.sp
                        )
                    )
                }
            } else {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) {
                    Text(stringResource(R.string.home_login_action))
                }
            }
        }
    }
}

@Composable
private fun HomeLoginHeader(loading: Boolean, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(
            if (loading) {
                Modifier.padding(top = 16.dp, bottom = 8.dp).heightIn(min = 64.dp)
            } else {
                Modifier.padding(vertical = 16.dp)
            }
        ),
        horizontalArrangement = Arrangement.spacedBy(if (loading) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(
                painterResource(if (loading) R.drawable.ic_chevron else R.drawable.ic_back),
                stringResource(R.string.setup_back),
                modifier = if (loading) Modifier.size(18.dp).rotate(180f) else Modifier,
                tint = if (loading) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    LocalContentColor.current
                }
            )
        }
        Text(
            stringResource(R.string.app_name),
            style = if (loading) {
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight(740),
                    letterSpacing = (-0.7).sp
                )
            } else {
                MaterialTheme.typography.titleLarge
            }
        )
    }
}

@Composable
private fun HomeLoginProgressEmblem() {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(222.dp).border(1.dp, colors.primary.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(168.dp).background(
                Brush.linearGradient(listOf(colors.primaryContainer, colors.secondaryContainer)),
                CircleShape
            )
        )
        QqLoadingEmblem(Modifier.offset(14.dp, 14.dp).size(196.dp))
    }
}

@Composable
private fun HomeLoginText(
    title: String,
    description: String,
    titleFocus: FocusRequester,
    loading: Boolean
) {
    val typography = MaterialTheme.typography
    Text(
        title,
        style = if (loading) {
            typography.headlineMedium.copy(
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight(750),
                letterSpacing = (-1.17).sp
            )
        } else {
            typography.headlineLarge
        },
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = if (loading) 17.dp else 0.dp)
            .focusRequester(titleFocus).focusable().semantics { heading() }
    )
    Text(
        description,
        modifier = if (loading) Modifier.padding(top = 12.dp).widthIn(max = 305.dp) else Modifier,
        style = if (loading) {
            typography.bodyMedium.copy(
                lineHeight = 22.sp
            )
        } else {
            typography.bodyLarge
        },
        color = if (loading) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            LocalContentColor.current
        },
        textAlign = TextAlign.Center
    )
}

@Composable
internal fun LoginRestorationNotice(
    state: RestorationState,
    refreshing: Boolean,
    onDismiss: () -> Unit
) {
    val message = stringResource(R.string.home_login_restored)
    val context = LocalContext.current
    LaunchedEffect(state, refreshing) {
        if (state == RestorationState.Restored && !refreshing) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onDismiss()
        }
    }
}
