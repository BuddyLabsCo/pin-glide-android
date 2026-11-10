package com.buddylabs.pinglide.ui.pairing

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.domain.pairing.PairingHost
import com.buddylabs.pinglide.platform.permissions.SetupPermissions

@Composable
fun PairingCameraGate(
    model: PairingEntryViewModel,
    home: HomeState,
    host: PairingHost,
    content: @Composable (() -> Unit) -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val permissions = remember(context) { SetupPermissions(context) }
    val snackbar = remember { SnackbarHostState() }
    var blockedRequestId by rememberSaveable { mutableStateOf<Long?>(null) }
    var permissionRequestId by rememberSaveable { mutableStateOf<Long?>(null) }
    DisposableEffect(model, host) {
        onDispose {
            snackbar.currentSnackbarData?.dismiss()
            if (activity?.isChangingConfigurations != true) model.cancelCameraRequest()
        }
    }
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) result@{ granted ->
        val requestId = permissionRequestId ?: return@result
        permissionRequestId = null
        if (!model.isCameraRequestCurrent(requestId, home)) return@result
        val rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        val blocked = !granted && rationale == false
        permissions.recordCameraRequestResult(blocked)
        if (granted) {
            model.cameraGranted(requestId, home)
        } else if (blocked) {
            blockedRequestId = requestId
        } else {
            Toast.makeText(context, R.string.pairing_camera_denied, Toast.LENGTH_LONG).show()
        }
    }
    CameraSettingsRecovery(model, home, snackbar, blockedRequestId) { blockedRequestId = null }
    Box(Modifier.fillMaxSize()) {
        content {
            if (permissionRequestId != null) return@content
            if (home.targets.size >= 5) {
                Toast.makeText(context, R.string.pairing_capacity, Toast.LENGTH_LONG).show()
            } else {
                val requestId = model.requestCamera(host, home)
                when {
                    context.checkSelfPermission(Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED ->
                        model.cameraGranted(requestId, home)

                    permissions.cameraRequestBlocked -> blockedRequestId = requestId

                    else -> {
                        permissionRequestId = requestId
                        request.launch(Manifest.permission.CAMERA)
                    }
                }
            }
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp)
        )
    }
}

@Composable
private fun CameraSettingsRecovery(
    model: PairingEntryViewModel,
    home: HomeState,
    snackbar: SnackbarHostState,
    blockedRequestId: Long?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val permissions = remember(context) { SetupPermissions(context) }
    var settingsRequestId by rememberSaveable { mutableStateOf<Long?>(null) }
    val settings =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) result@{
            val requestId = settingsRequestId ?: return@result
            settingsRequestId = null
            if (!model.isCameraRequestCurrent(requestId, home)) return@result
            if (context.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                model.cameraGranted(requestId, home)
            } else {
                Toast.makeText(context, R.string.pairing_camera_denied, Toast.LENGTH_LONG).show()
            }
        }
    val blockedText = stringResource(R.string.pairing_camera_blocked)
    val settingsText = stringResource(R.string.pairing_open_settings)
    LaunchedEffect(home.owner, home.generation) {
        if (blockedRequestId?.let { !model.isCameraRequestCurrent(it, home) } == true) {
            onDismiss()
            snackbar.currentSnackbarData?.dismiss()
        }
    }
    LaunchedEffect(blockedRequestId) {
        val requestId = blockedRequestId
        if (requestId != null && model.isCameraRequestCurrent(requestId, home)) {
            val result = snackbar.showSnackbar(
                blockedText,
                settingsText,
                true,
                SnackbarDuration.Indefinite
            )
            onDismiss()
            if (result == SnackbarResult.ActionPerformed &&
                model.isCameraRequestCurrent(requestId, home)
            ) {
                settingsRequestId = requestId
                if (!permissions.openApplicationSettings { settings.launch(it) }) {
                    settingsRequestId = null
                    Toast.makeText(
                        context,
                        R.string.pairing_settings_unavailable,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
