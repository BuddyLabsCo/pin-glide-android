package com.buddylabs.pinglide.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.HomeReadiness
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.PairingChange
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
fun HomeScreen(
    state: HomeState,
    smsGranted: Boolean,
    smsRecovery: SmsRecovery,
    home: HomeViewModel,
    onRestoreSms: () -> Unit,
    onAdd: () -> Unit
) {
    val readiness = state.readiness(smsGranted)
    val action by home.action.collectAsState()
    val restoration by home.loginRestoration.collectAsState()
    val listState = rememberLazyListState()
    var selectedId by rememberSaveable(state.owner) { mutableStateOf<String?>(null) }
    var lastSelectedId by rememberSaveable(state.owner) { mutableStateOf<String?>(null) }
    var restoreFocus by remember { mutableStateOf(false) }
    val rowFocus = remember { FocusRequester() }
    val headingFocus = remember { FocusRequester() }
    val selected = state.targets.find { it.pairingId == selectedId }
    val selectedAction = action.takeIf { it.pairingId == selectedId && it.owner == state.owner }
        ?: TargetAction()
    val dismiss = {
        selectedId = null
        restoreFocus = true
    }
    LaunchedEffect(restoreFocus) {
        if (restoreFocus) {
            listState.revealTarget(
                state.targets.indexOfFirst {
                    it.pairingId == lastSelectedId
                },
                lastSelectedId
            )
            withFrameNanos { }
            val restored = try {
                rowFocus.requestFocus()
            } catch (_: IllegalStateException) {
                false
            }
            if (!restored) headingFocus.requestFocus()
            restoreFocus = false
        }
    }
    LaunchedEffect(selectedAction.result) {
        if (selectedAction.result == PairingChange.Removed) dismiss()
    }
    if (selectedId != null && selected == null && state.listed && !selectedAction.pending &&
        selectedAction.result != PairingChange.Removed
    ) {
        BackHandler(onBack = dismiss)
        TargetGone(onDismiss = dismiss, onAdd = {
            dismiss()
            onAdd()
        })
        return
    }
    HomeContent(
        state, readiness, action, smsGranted, smsRecovery, onRestoreSms,
        home::refresh, { pairingId, enabled ->
            home.select(state, pairingId, enabled)
        },
        onOpen = {
            home.clearAction()
            lastSelectedId = it
            selectedId = it
        },
        onAdd, home::restoreLogin, lastSelectedId, rowFocus, headingFocus, listState
    )
    LoginRestorationNotice(restoration, state.refreshing, home::dismissLoginNotice)
    SmsRecoveryNotice(state, smsGranted, smsRecovery, home::dismissSmsFeedback)
    if (selected != null) {
        TargetDetailsSheet(
            selected,
            otherAliases = state.targets.filter {
                it.pairingId != selected.pairingId
            }.map { it.alias },
            action = selectedAction,
            onSelect = { home.select(state, selected.pairingId, it) },
            onRename = { home.rename(state, selected.pairingId, it) },
            onRemove = { home.remove(state, selected.pairingId) },
            onClearAction = home::clearAction,
            onDismiss = dismiss
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeState,
    readiness: HomeReadiness,
    action: TargetAction,
    smsGranted: Boolean,
    smsRecovery: SmsRecovery,
    onRestoreSms: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onRestoreLogin: () -> Unit,
    lastSelectedId: String?,
    rowFocus: FocusRequester,
    headingFocus: FocusRequester,
    listState: LazyListState
) {
    LaunchedEffect(Unit) { headingFocus.requestFocus() }
    ScreenLayout {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 20.dp).focusRequester(headingFocus)
                .focusable().semantics { heading() }
        )
        HomeRefreshContainer(state, onRefresh, Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "home-summary") {
                    HomeSummary(
                        state,
                        readiness,
                        action,
                        smsGranted,
                        smsRecovery,
                        onRestoreSms,
                        onAdd,
                        onRestoreLogin
                    )
                }
                items(state.targets, key = { it.pairingId }) { target ->
                    TargetRow(
                        target,
                        enabled = readiness != HomeReadiness.LoginRequired,
                        onOpen = {
                            onOpen(target.pairingId)
                        },
                        onSelect = { onSelect(target.pairingId, it) },
                        modifier = if (target.pairingId == lastSelectedId) {
                            Modifier.focusRequester(rowFocus)
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
        if (state.targets.isNotEmpty() && state.targets.size < 5 && canAdd(readiness)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Text("+") },
                    text = { Text(stringResource(R.string.home_add)) }
                )
            }
        }
    }
}

@Composable
private fun HomeSummary(
    state: HomeState,
    readiness: HomeReadiness,
    action: TargetAction,
    smsGranted: Boolean,
    smsRecovery: SmsRecovery,
    onRestoreSms: () -> Unit,
    onAdd: () -> Unit,
    onRestoreLogin: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HomeHero(state, readiness)
        if (readiness == HomeReadiness.LoginRequired) {
            Button(
                onClick = onRestoreLogin,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.home_login_action))
            }
        } else if (!smsGranted) {
            HomeSmsRecovery(smsRecovery == SmsRecovery.Pending, onRestoreSms)
        }
        if (action.result == PairingChange.Removed && action.owner == state.owner) {
            Text(
                stringResource(R.string.home_removed),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        if (state.failed && state.targets.isNotEmpty()) {
            Text(
                stringResource(R.string.home_cached_notice),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (state.listed) DeviceCount(state.targets.size)
        if (state.targets.isEmpty()) EmptyRegion(state, readiness, onAdd)
    }
}

private suspend fun LazyListState.revealTarget(index: Int, pairingId: String?) {
    if (index >= 0 && layoutInfo.visibleItemsInfo.none { it.key == pairingId }) {
        scrollToItem(index + 1)
    }
}

@Composable
private fun TargetGone(onDismiss: () -> Unit, onAdd: () -> Unit) {
    ScreenLayout {
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                stringResource(R.string.home_gone_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                }
            )
            Text(stringResource(R.string.home_gone_copy))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.home_return_list))
            }
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.home_add_again))
            }
        }
    }
}

@Composable
private fun DeviceCount(count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            stringResource(R.string.home_devices),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            if (count >= 5) {
                stringResource(R.string.home_capacity)
            } else {
                stringResource(R.string.home_count, count)
            },
            color = if (count >= 5) {
                colorResource(R.color.home_capacity)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun EmptyRegion(state: HomeState, readiness: HomeReadiness, onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.listed) {
            Text(
                stringResource(R.string.home_no_devices),
                style = MaterialTheme.typography.titleLarge
            )
            Text(stringResource(R.string.home_add_instructions))
            if (canAdd(readiness)) {
                Button(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.home_add_first))
                }
            }
        } else {
            Text(
                stringResource(
                    if (state.failed) {
                        R.string.home_load_error_region
                    } else {
                        R.string.home_loading_region
                    }
                ),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
    }
}

private fun canAdd(readiness: HomeReadiness): Boolean = readiness !in listOf(
    HomeReadiness.Revoked,
    HomeReadiness.LoginRequired,
    HomeReadiness.Unknown
)
