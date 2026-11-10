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
    content: @Composable ((PairingHost) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val permissions = remember(context) { SetupPermissions(context) }
    val snackbar = remember { SnackbarHostState() }
    var blockedNotice by rememberSaveable { mutableStateOf(false) }
    var requesting by remember { mutableStateOf(false) }
    val settings =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (context.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                model.cameraGranted(home)
            } else {
                Toast.makeText(context, R.string.pairing_camera_denied, Toast.LENGTH_LONG).show()
            }
        }
    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        requesting = false
        val rationale = activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        val blocked = !granted && rationale == false
        permissions.recordCameraRequestResult(blocked)
        if (granted) {
            model.cameraGranted(home)
        } else if (blocked) {
            blockedNotice = true
        } else {
            Toast.makeText(context, R.string.pairing_camera_denied, Toast.LENGTH_LONG).show()
        }
    }
    val blockedText = stringResource(R.string.pairing_camera_blocked)
    val settingsText = stringResource(R.string.pairing_open_settings)
    LaunchedEffect(blockedNotice) {
        if (blockedNotice) {
            val result = snackbar.showSnackbar(
                blockedText,
                settingsText,
                true,
                SnackbarDuration.Indefinite
            )
            blockedNotice = false
            if (result == SnackbarResult.ActionPerformed &&
                !permissions.openApplicationSettings { settings.launch(it) }
            ) {
                Toast.makeText(
                    context,
                    R.string.pairing_settings_unavailable,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        content { host ->
            if (requesting) return@content
            if (home.targets.size >= 5) {
                Toast.makeText(context, R.string.pairing_capacity, Toast.LENGTH_LONG).show()
            } else {
                model.requestCamera(host, home)
                when {
                    context.checkSelfPermission(Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED ->
                        model.cameraGranted(home)

                    permissions.cameraRequestBlocked -> blockedNotice = true

                    else -> {
                        requesting = true
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
