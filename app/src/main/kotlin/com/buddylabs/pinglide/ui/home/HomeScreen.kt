package com.buddylabs.pinglide.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.buddylabs.pinglide.ui.components.ScreenLayout

@Composable
fun HomeScreen(
    state: HomeState,
    smsGranted: Boolean,
    onRefresh: () -> Unit,
    onSelect: (String, Boolean) -> Unit,
    onAdd: () -> Unit
) {
    val readiness = state.readiness(smsGranted)
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = state.targets.find { it.pairingId == selectedId }
    ScreenLayout {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(vertical = 20.dp).semantics { heading() }
        )
        HomeRefreshContainer(state, onRefresh, Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { HomeHero(state, readiness) }
                if (state.failed && state.targets.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.home_cached_notice),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (state.listed) {
                    item { DeviceCount(state.targets.size) }
                }
                if (state.targets.isEmpty()) {
                    item {
                        EmptyRegion(state, readiness, onAdd)
                    }
                }
                items(state.targets, key = { it.pairingId }) { target ->
                    TargetRow(target, { selectedId = target.pairingId }) {
                        onSelect(target.pairingId, it)
                    }
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
    if (selected != null) {
        AlertDialog(
            onDismissRequest = { selectedId = null },
            title = {
                Text(selected.alias.ifBlank { stringResource(R.string.home_target_device) })
            },
            text = { Text(targetPresence(selected)) },
            confirmButton = {
                TextButton(
                    onClick = { selectedId = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        )
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
