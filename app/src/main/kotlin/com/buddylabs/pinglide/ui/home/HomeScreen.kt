package com.buddylabs.pinglide.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.HomeReadiness
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.PairingChange
import com.buddylabs.pinglide.domain.pairing.TargetDevice
import com.buddylabs.pinglide.ui.components.BrandIcon
import com.buddylabs.pinglide.ui.components.ScreenLayout
import java.time.Instant
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: HomeState,
    smsGranted: Boolean,
    smsRecovery: SmsRecovery,
    home: HomeViewModel,
    onRestoreSms: () -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    highlightedPairingId: String? = null
) {
    val readiness = state.readiness(smsGranted)
    val action by home.action.collectAsState()
    val restoration by home.loginRestoration.collectAsState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current.density
    val outlineClearance = (TargetOutlineClearance.value * density).roundToInt()
    var selectedId by rememberSaveable(state.owner) { mutableStateOf<String?>(null) }
    var lastSelectedId by rememberSaveable(state.owner) { mutableStateOf<String?>(null) }
    var restoreFocus by remember { mutableStateOf(false) }
    val rowFocus = remember { FocusRequester() }
    val headingFocus = remember { FocusRequester() }
    val selected = state.targets.find { it.pairingId == selectedId }
    val selectedAction = action.takeIf { it.pairingId == selectedId && it.owner == state.owner }
        ?: TargetAction()
    val dismiss = {
        home.showTestTarget(null)
        selectedId = null
        restoreFocus = true
    }
    LaunchedEffect(
        selectedId,
        state.owner,
        state.generation,
        selected?.alias,
        selected?.keyUsable
    ) {
        home.showTestTarget(selectedId)
    }
    LaunchedEffect(highlightedPairingId) {
        if (highlightedPairingId != null) {
            selectedId = null
            lastSelectedId = highlightedPairingId
            withFrameNanos { }
            listState.revealTarget(
                state.targets.indexOfFirst { it.pairingId == highlightedPairingId },
                highlightedPairingId,
                outlineClearance
            )
        }
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
    val now by rememberPresenceTime(state.targets)
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
        onAdd,
        home::restoreLogin,
        {
            dismiss()
            onSettings()
        },
        lastSelectedId,
        rowFocus,
        headingFocus,
        listState,
        highlightedPairingId,
        rememberDuplicateNotice(highlightedPairingId, state.owner),
        now
    )
    LoginRestorationNotice(restoration, state.refreshing, home::dismissLoginNotice)
    SmsRecoveryNotice(state, smsGranted, smsRecovery, home::dismissSmsFeedback)
    if (selected != null) {
        TargetDetailsSheet(
            selected,
            presenceLabel = targetPresence(selected, now),
            otherAliases = state.targets.filter {
                it.pairingId != selected.pairingId
            }.map { it.alias },
            action = selectedAction,
            testMessage = home.testMessage,
            onSelect = { home.select(state, selected.pairingId, it) },
            onRename = { home.rename(state, selected.pairingId, it) },
            onRemove = { home.remove(state, selected.pairingId) },
            onClearAction = home::clearAction,
            onDismiss = dismiss
        )
    }
}

@Composable
private fun rememberPresenceTime(targets: List<TargetDevice>): State<Instant> =
    produceState(Instant.now(), targets) {
        while (targets.isNotEmpty()) {
            value = Instant.now()
            delay(60_000)
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
    onSettings: () -> Unit,
    lastSelectedId: String?,
    rowFocus: FocusRequester,
    headingFocus: FocusRequester,
    listState: LazyListState,
    highlightedPairingId: String?,
    showDuplicateNotice: Boolean,
    now: Instant
) {
    LaunchedEffect(Unit) { headingFocus.requestFocus() }
    ScreenLayout {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewportHeight = maxHeight
            val showAdd = state.targets.isNotEmpty() && state.targets.size < 5 && canAdd(readiness)
            Column(Modifier.fillMaxSize()) {
                HomeHeader(headingFocus, onSettings)
                HomeRefreshContainer(state, onRefresh, Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = TargetOutlineClearance),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "home-summary") {
                            HomeSummary(
                                state,
                                readiness,
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
                                highlighted = target.pairingId == highlightedPairingId,
                                now = now,
                                modifier = if (target.pairingId == lastSelectedId) {
                                    Modifier.focusRequester(rowFocus)
                                } else {
                                    Modifier
                                }
                            )
                        }
                    }
                }
                HomeFooterNotice(
                    showDuplicateNotice,
                    action.result == PairingChange.Removed && action.owner == state.owner,
                    showAdd,
                    viewportHeight
                )
                if (showAdd) {
                    AddDeviceAction(
                        onAdd,
                        Modifier.align(
                            Alignment.End
                        ).padding(top = HomeAddTop, bottom = HomeAddBottom)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddDeviceAction(onAdd: () -> Unit, modifier: Modifier) {
    FloatingActionButton(
        onClick = onAdd,
        modifier = modifier.heightIn(min = HomeAddHeight),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(R.drawable.ic_add), null, Modifier.size(20.dp))
            Text(
                stringResource(R.string.home_add),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun HomeHeader(headingFocus: FocusRequester, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp).heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        BrandIcon(Modifier.size(36.dp))
        Column(
            Modifier.weight(1f).focusRequester(headingFocus).focusable()
                .semantics(mergeDescendants = true) { heading() }
        ) {
            Text(
                stringResource(R.string.app_name),
                fontSize = 20.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight(740),
                letterSpacing = (-0.7).sp
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
        IconButton(onClick = onSettings) {
            Icon(
                painterResource(R.drawable.ic_settings_filled),
                stringResource(R.string.settings_title),
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HomeSummary(
    state: HomeState,
    readiness: HomeReadiness,
    smsGranted: Boolean,
    smsRecovery: SmsRecovery,
    onRestoreSms: () -> Unit,
    onAdd: () -> Unit,
    onRestoreLogin: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(
            if (readiness == HomeReadiness.Empty) 14.dp else 16.dp
        )
    ) {
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
        if (state.failed && state.targets.isNotEmpty()) {
            Text(
                stringResource(R.string.home_cached_notice),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (readiness == HomeReadiness.Empty) {
            HomeEmptyCard(onAdd)
        } else {
            if (state.listed) DeviceCount(state.targets.size)
            if (state.targets.isEmpty()) EmptyRegion(state, readiness, onAdd)
        }
    }
}

private suspend fun LazyListState.revealTarget(index: Int, pairingId: String?, clearance: Int = 0) {
    if (index < 0) return
    val layout = layoutInfo
    val item = layout.visibleItemsInfo.find { it.key == pairingId }
    if (item == null || item.offset < layout.viewportStartOffset + clearance ||
        item.offset + item.size > layout.viewportEndOffset - clearance
    ) {
        scrollToItem(index + 1, -clearance)
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
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.home_devices),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight(730),
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        Text(
            if (count >= 5) {
                stringResource(R.string.home_capacity)
            } else {
                stringResource(R.string.home_count, count)
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
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
