package com.buddylabs.pinglide.ui.pairing

import android.Manifest
import android.content.ComponentName
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.MainActivity
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.LoginState
import com.buddylabs.pinglide.domain.identity.SenderRestoration
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.domain.pairing.PairingActivation
import com.buddylabs.pinglide.domain.pairing.PairingEntry
import com.buddylabs.pinglide.domain.pairing.PairingEntryPage
import com.buddylabs.pinglide.domain.pairing.PairingQr
import com.buddylabs.pinglide.domain.pairing.PairingSession
import com.buddylabs.pinglide.domain.pairing.PairingSessionService
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.domain.settings.SetupProgress
import com.buddylabs.pinglide.domain.settings.SetupState
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.home.HomePairingStore
import com.buddylabs.pinglide.ui.home.HomeViewModel
import com.buddylabs.pinglide.ui.navigation.AcceptedNavigation
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingConsentNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Main)
    private val store = HomePairingStore(emptyList(), Instant.now().plusSeconds(3600).toString())
    private val renewal = CredentialRenewal(store, { _, _ -> error("Unexpected renewal") }, scope)
    private val pairings = OwnPairings(store, { emptyList() }, renewal, scope)
    private val sessions = object : PairingSessionService {
        private val expiry = Instant.now().plusSeconds(270)

        override suspend fun authorize(token: String, sessionId: String) = PairingSession(
            sessionId, "receiver", "sender", "pairing", "new_receiver", "awaiting_confirmation",
            expiry, false, false
        )

        override suspend fun cancel(token: String, sessionId: String) = Unit
        override fun watch(token: String, sessionId: String) = emptyFlow<PairingSession>()
    }
    private val pairing = PairingEntryViewModel(
        { entryScope ->
            PairingEntry(
                store,
                pairings,
                sessions,
                { PairingQr("session", "receiver", ByteArray(0), ByteArray(0)) },
                renewal,
                entryScope,
                activation = PairingActivation(
                    store,
                    pairings,
                    sessions,
                    { error("Unexpected handshake") },
                    renewal
                )
            )
        },
        { error("Unexpected test delivery") }
    )
    private val home = HomeViewModel(
        pairings,
        SenderRestoration(
            store,
            { error("Unexpected QQ authorization") },
            { _, _, _ -> error("Unexpected login") },
            renewal,
            { "Sender" }
        )
    ) { null }

    @After
    fun stopFixture() {
        pairing.viewModelScope.cancel()
        home.viewModelScope.cancel()
        scope.cancel()
    }

    @Test
    fun backReturnsToScannerAndCancelReturnsToFirstDeviceEntry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.CAMERA
        )
        configureWindow()
        val activity = compose.activity
        compose.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides activity,
                LocalDensity provides Density(LocalDensity.current.density, 1.75f)
            ) {
                LocalizedContent(AppLanguage.English) {
                    PinGlideTheme {
                        AcceptedNavigation(
                            progress = FirstDeviceSetup,
                            smsGranted = true,
                            onCheckSms = { true },
                            loginState = LoginState.FirstDevice,
                            home = home,
                            pairing = pairing,
                            onRestoreSms = {},
                            onBackground = {},
                            onNewRegistration = {},
                            settings = {},
                            setup = {}
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Connect your first device").assertIsDisplayed()
        compose.onNodeWithText("Scan QR code").performScrollTo().performClick()
        compose.onNodeWithTag("pairing-scanner").assertIsDisplayed()
        scan()
        editAliasAcrossKeyboardResize()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("pairing-scanner").assertIsDisplayed()
        scan()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Connect your first device").assertIsDisplayed()
        compose.onNodeWithText("Confirm Pairing").assertDoesNotExist()
        compose.onNodeWithTag("pairing-scanner").assertDoesNotExist()
    }

    @Suppress("DEPRECATION")
    private fun configureWindow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mode = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0
        ).softInputMode
        assertEquals(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            mode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
        )
        compose.activityRule.scenario.onActivity {
            it.enableEdgeToEdge()
            it.window.setSoftInputMode(mode)
        }
    }

    private fun editAliasAcrossKeyboardResize() {
        val activity = compose.activity
        val title = compose.onNodeWithText("Confirm Pairing")
        val confirm = compose.onNodeWithText("Confirm and establish key")
        val anchored = confirm.assertIsDisplayed().getUnclippedBoundsInRoot()
        val alias = compose.onNodeWithContentDescription("Target name")
        alias.performScrollTo().performTouchInput { click() }
        compose.waitUntil(5_000) {
            ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        alias.performScrollTo().assertIsDisplayed().performTextReplacement("Renamed device")
        confirm.performScrollTo().assertIsDisplayed()
        title.assertIsNotDisplayed()
        alias.performScrollTo().assertIsDisplayed().assertTextContains("Renamed device")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) {
            ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == false
        }
        title.assertIsDisplayed()
        alias.performScrollTo().assertIsDisplayed().performTextReplacement("Final device")
        alias.assertTextContains("Final device")
        assertEquals(anchored, confirm.assertIsDisplayed().getUnclippedBoundsInRoot())
    }

    private fun scan() {
        compose.runOnIdle { pairing.scan("fixture", "My device") }
        compose.waitUntil(5_000) { pairing.state.value.page == PairingEntryPage.Consent }
        compose.onNodeWithText("Confirm Pairing").assertIsDisplayed()
    }
}

private object FirstDeviceSetup : SetupProgress {
    override val state = SetupState(started = true, smsCompleted = true, batteryAcknowledged = true)
    override fun start() = error("Unexpected setup")
    override fun recordSmsRequest() = error("Unexpected permission request")
    override fun completeSmsPrerequisite() = error("Unexpected permission request")
    override fun acknowledgeBattery() = error("Unexpected battery setup")
    override fun complete() = error("Cancel must not complete onboarding")
}
