package com.buddylabs.pinglide.ui.pairing

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.domain.identity.AuthorizationState
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.LoginState
import com.buddylabs.pinglide.domain.identity.SenderRestoration
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.domain.pairing.PairingEntryPage
import com.buddylabs.pinglide.domain.pairing.PairingHost
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.domain.settings.SetupProgress
import com.buddylabs.pinglide.domain.settings.SetupState
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.home.HomePairingStore
import com.buddylabs.pinglide.ui.home.HomeViewModel
import com.buddylabs.pinglide.ui.navigation.AcceptedNavigation
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

internal fun AndroidComposeTestRule<*, ComponentActivity>.withPairingSuccessNavigation(
    model: PairingEntryViewModel,
    pairings: OwnPairings,
    store: HomePairingStore,
    renewal: CredentialRenewal,
    host: PairingHost,
    verifyActivation: () -> Unit
) {
    val onboarding = host == PairingHost.Onboarding
    val progress = SuccessSetupProgress(completed = !onboarding)
    val home = HomeViewModel(
        pairings,
        SenderRestoration(
            store,
            { error("Unexpected QQ authorization") },
            { _, _, _ -> error("Unexpected login") },
            renewal,
            { "Sender" }
        )
    ) { error("Unexpected Home test delivery") }
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.uiAutomation.grantRuntimePermission(
        instrumentation.targetContext.packageName,
        Manifest.permission.CAMERA
    )
    runOnUiThread { activity.enableEdgeToEdge() }
    val owner = activity
    try {
        setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                LocalizedContent(AppLanguage.SimplifiedChinese) {
                    PinGlideTheme {
                        AcceptedNavigation(
                            progress, true, { true }, LoginState.FirstDevice, home, model,
                            onRestoreSms = {}, onBackground = {}, onNewRegistration = {},
                            settings = {}, setup = { error("Unexpected setup") }
                        )
                    }
                }
            }
        }
        waitUntil(5_000) {
            home.state.value.authorization == AuthorizationState.Ready &&
                home.state.value.listed && !home.state.value.refreshing
        }
        onNodeWithText(if (onboarding) "扫描二维码" else "添加第一台设备").performClick()
        waitUntil(5_000) { model.state.value.page == PairingEntryPage.Scanner }
        runOnIdle { model.scan("session", "My device") }
        waitUntil(5_000) { model.state.value.page == PairingEntryPage.Consent }
        assertEquals(host, model.state.value.host)
        onNodeWithText(if (onboarding) "确认并建立密钥" else "确认添加设备").performClick()
        onNodeWithText("正在生成设备身份").assertIsDisplayed()
        verifyActivation()
        onNodeWithText("我的设备").assertIsDisplayed()
        onNodeWithText("My device").assertIsDisplayed()
        assertTrue(progress.state.completed)
        assertEquals(PairingEntryPage.Idle, model.state.value.page)
    } finally {
        home.viewModelScope.cancel()
    }
}

private class SuccessSetupProgress(completed: Boolean) : SetupProgress {
    override var state = SetupState(
        started = true,
        smsCompleted = true,
        batteryAcknowledged = true,
        completed = completed
    )
        private set

    override fun start() = error("Unexpected setup")
    override fun recordSmsRequest() = error("Unexpected SMS request")
    override fun completeSmsPrerequisite() = error("Unexpected SMS prerequisite")
    override fun acknowledgeBattery() = error("Unexpected battery setup")
    override fun complete() {
        state = state.copy(completed = true)
    }
}

internal fun ComposeContentTestRule.assertExpiredResult() {
    onNode(
        hasText("QR code expired") and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
    ).assertIsDisplayed()
    onNodeWithText("Pairing incomplete").assertDoesNotExist()
    onNodeWithText("Exchanging encryption keys").assertDoesNotExist()
    onNodeWithText("Pair later").assertIsDisplayed()
}

internal fun ComposeContentTestRule.assertConfirmationVisible() {
    onNodeWithText("正在确认配对信息").assertIsDisplayed()
    onNodeWithText("正在打开配对通道").assertDoesNotExist()
    onNodeWithText("正在生成设备身份").assertDoesNotExist()
    onNodeWithText("正在交换加密密钥").assertDoesNotExist()
    onNodeWithText("配对完成").assertDoesNotExist()
    onNodeWithText("保持接收端上的鹊信窗口打开。完成后会自动进入配对完成页面。").assertIsDisplayed()
    assertAccessiblePairingProgress()
}
