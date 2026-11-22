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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.buddylabs.pinglide.ui.components.RingedEmblem
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
        HomeLoginHeader(onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(
                    when (state) {
                        RestorationState.Loading -> PaddingValues(top = 12.dp, bottom = 53.dp)
                        RestorationState.Cancelled -> PaddingValues(top = 12.dp, bottom = 58.dp)
                        else -> PaddingValues(top = 12.dp, bottom = 63.dp)
                    }
                ).semantics {
                    paneTitle = title
                    liveRegion = LiveRegionMode.Polite
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HomeLoginEmblem(loading)
            HomeLoginText(
                title,
                stringResource(strings.second),
                titleFocus,
                state
            )
        }
        HomeLoginAction(state, onCancel, onRetry)
    }
}

@Composable
private fun HomeLoginAction(state: RestorationState, onCancel: () -> Unit, onRetry: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().imePadding().heightIn(min = 48.dp)
    if (state == RestorationState.Loading) {
        TextButton(onClick = onCancel, modifier = modifier) {
            Text(
                stringResource(R.string.setup_cancel),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.14.sp
                )
            )
        }
    } else {
        Button(onClick = onRetry, modifier = modifier) {
            HomeActionLabel(R.drawable.ic_account_circle, R.string.home_login_action)
        }
    }
}

@Composable
private fun HomeLoginHeader(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp).heightIn(min = 64.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(
                painterResource(R.drawable.ic_back_chevron),
                stringResource(R.string.setup_back),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight(740),
                letterSpacing = (-0.7).sp
            )
        )
    }
}

@Composable
private fun HomeLoginEmblem(loading: Boolean) {
    if (!loading) {
        RingedEmblem(RoundedCornerShape(44.dp)) { QqEmblem() }
        return
    }
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
    state: RestorationState
) {
    val typography = MaterialTheme.typography
    val cancelled = state == RestorationState.Cancelled
    val titleSpacing = when (state) {
        RestorationState.Loading -> 17.dp
        RestorationState.Cancelled -> 4.dp
        else -> 7.dp
    }
    Text(
        title,
        style = typography.headlineMedium.copy(
            fontSize = 26.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight(750),
            letterSpacing = (-1.17).sp
        ),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = titleSpacing)
            .focusRequester(titleFocus).focusable().semantics { heading() }
    )
    Text(
        description,
        modifier = Modifier.padding(top = if (cancelled) 8.dp else 12.dp).widthIn(max = 305.dp),
        style = typography.bodyMedium.copy(
            lineHeight = 22.sp,
            letterSpacing = if (cancelled) 0.sp else typography.bodyMedium.letterSpacing
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
