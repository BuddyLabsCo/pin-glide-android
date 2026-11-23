package com.buddylabs.pinglide.ui.home

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.LoginState
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.domain.pairing.PairingActivation
import com.buddylabs.pinglide.domain.pairing.PairingChange
import com.buddylabs.pinglide.domain.pairing.PairingEntry
import com.buddylabs.pinglide.domain.pairing.PairingQr
import com.buddylabs.pinglide.domain.pairing.PairingService
import com.buddylabs.pinglide.domain.pairing.PairingSessionService
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.domain.settings.SetupProgress
import com.buddylabs.pinglide.domain.settings.SetupState
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.navigation.AcceptedNavigation
import com.buddylabs.pinglide.ui.pairing.PairingEntryViewModel
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HomeDuplicateScanTest {
    private val motionDuration = object : MotionDurationScale {
        override val scaleFactor = 1f
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(effectContext = motionDuration)

    private val scope = CoroutineScope(Dispatchers.Main)
    private var primary = 0
    private var density = 1f

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun scannerCentersItsHeadingAndKeepsCancelAtTheBottom() {
        renderHome(usableHeight = 700.dp)
        val add = compose.onNodeWithText("Add device")
        val title = compose.onNodeWithText("Scan QR code")
        val cancel = compose.onNodeWithText("Cancel")
        val close = compose.onNodeWithContentDescription("Cancel")
        add.performClick()

        val screenBounds = compose.onNode(hasScrollAction()).getUnclippedBoundsInRoot()
        val titleBounds = title.assertIsDisplayed().getUnclippedBoundsInRoot()
        val cancelBounds = cancel.assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(
            (screenBounds.left + screenBounds.right).value / 2,
            (titleBounds.left + titleBounds.right).value / 2,
            1f
        )
        assertEquals(28f, (screenBounds.bottom - cancelBounds.bottom).value, 1f)
        assertTrue(cancelBounds.bottom - cancelBounds.top >= 48.dp)

        cancel.performClick()
        add.assertIsDisplayed().performClick()
        close.performClick()
        add.assertIsDisplayed()
    }

    @Test
    fun scannerCancelRemainsReachableWithLargeTextInAShortWindow() {
        renderHome(usableHeight = 260.dp, fontScale = 1.5f)
        val add = compose.onNodeWithText("Add device")
        add.performClick()
        compose.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed().performClick()
        add.assertIsDisplayed()
    }

    @Test
    fun duplicateScanRevealsLastTargetAndFadesBeforeAccessibleNoticeExpires() {
        val fixture = renderHome()
        val add = compose.onNodeWithText("Add device")
        val notice = compose.onNodeWithText("This target is already added")
        val cue = compose.onNodeWithText("Just scanned device", useUnmergedTree = true)
        compose.runOnIdle { fixture.home.remove(fixture.home.state.value, "remove") }
        compose.waitUntil(3_000) {
            fixture.home.action.value.result == PairingChange.Removed
        }
        compose.onNodeWithText("Pairing removed").assertIsDisplayed()

        add.performClick()
        compose.onNodeWithText("Scan QR code").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        val scanStartedAt = System.currentTimeMillis()
        compose.runOnIdle { fixture.pairing.scan("receiver-office", "Unused") }
        val composeScanTime = compose.mainClock.currentTime
        compose.waitUntil(2_000) { fixture.pairing.state.value.duplicatePairingId == "office" }
        compose.mainClock.advanceTimeBy(160)
        val deadline = fixture.pairing.state.value.highlightUntil
        assertTrue(deadline >= scanStartedAt + 3_000)
        assertTrue(deadline <= System.currentTimeMillis() + 3_000)
        notice.assertIsDisplayed()
        cue.assertIsDisplayed()
        compose.onNodeWithText("Pairing removed").assertDoesNotExist()
        assertTrue(notice.getUnclippedBoundsInRoot().bottom < add.getUnclippedBoundsInRoot().top)
        val list = compose.onNode(hasScrollAction()).getUnclippedBoundsInRoot()
        val switch = compose.onNodeWithContentDescription("Receive SMS on Office computer")
            .assertIsDisplayed().assertIsOff()
        assertTrue(cue.getUnclippedBoundsInRoot().bottom + 15.dp <= list.bottom)
        val switchBounds = switch.getUnclippedBoundsInRoot()
        val outlineX = list.left - 4.dp
        val outlineY = (switchBounds.top + switchBounds.bottom) / 2
        assertEquals(primary, pixelAt(outlineX, outlineY))

        compose.waitUntil(3_000) { System.currentTimeMillis() >= deadline - 600 }
        assertEquals("office", fixture.pairing.state.value.duplicatePairingId)
        assertEquals(primary, pixelAt(outlineX, outlineY))
        compose.waitUntil(2_000) { fixture.pairing.state.value.duplicatePairingId == null }
        compose.mainClock.advanceTimeBy(112)
        val fading = pixelAt(outlineX, outlineY)
        assertNotEquals(primary, fading)
        compose.mainClock.advanceTimeBy(300)
        assertNotEquals(fading, pixelAt(outlineX, outlineY))
        cue.assertDoesNotExist()
        notice.assertIsDisplayed()
        assertEquals(
            mapOf("home" to true, "office" to false),
            runBlocking {
                fixture.store.readPairings().items.associate { it.pairingId to it.enabled }
            }
        )
        compose.mainClock.advanceTimeBy(4_500 - (compose.mainClock.currentTime - composeScanTime))
        notice.assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_800)
        notice.assertDoesNotExist()
    }

    @Test
    fun shortHomeRevealsScanCueThroughTheStandardTimeout() {
        val fixture = renderHome(usableHeight = 260.dp, fontScale = 1.5f, textTimeout = 3_000L)
        val add = compose.onNodeWithText("Add device").assertIsDisplayed()
        val notice = compose.onNodeWithText("This target is already added")
        val cue = compose.onNodeWithText("Just scanned device", useUnmergedTree = true)
        add.performClick()
        compose.onNodeWithText("Scan QR code").assertIsDisplayed()
        compose.runOnIdle { fixture.pairing.scan("receiver-office", "Unused") }
        compose.waitUntil(2_000) { fixture.pairing.state.value.duplicatePairingId == "office" }
        val list = compose.onNode(hasScrollAction())
        val cueBounds = cue.assertIsDisplayed().getUnclippedBoundsInRoot()
        val listBounds = list.getUnclippedBoundsInRoot()
        assertTrue(cueBounds.top >= listBounds.top && cueBounds.bottom <= listBounds.bottom)
        assertTrue(notice.getUnclippedBoundsInRoot().bottom < add.getUnclippedBoundsInRoot().top)
        compose.waitUntil(4_000) { fixture.pairing.state.value.duplicatePairingId == null }
        compose.mainClock.advanceTimeBy(3_000)
        notice.assertDoesNotExist()
        cue.assertDoesNotExist()
        val expandedList = list.getUnclippedBoundsInRoot()
        assertTrue(expandedList.bottom - expandedList.top > listBounds.bottom - listBounds.top)
        add.assertIsDisplayed()
    }

    private fun pixelAt(horizontal: Dp, vertical: Dp): Int =
        compose.onRoot().captureToImage().asAndroidBitmap().getPixel(
            (horizontal.value * density).toInt(),
            (vertical.value * density).toInt()
        )

    private fun renderHome(
        usableHeight: Dp = 520.dp,
        fontScale: Float = 1f,
        textTimeout: Long = 6_000L
    ): DuplicateScanFixture {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PinGlideApplication
        instrumentation.uiAutomation.grantRuntimePermission(
            app.packageName,
            Manifest.permission.CAMERA
        )
        val fixture = DuplicateScanFixture(app, scope)
        val accessibility = object : AccessibilityManager {
            override fun calculateRecommendedTimeoutMillis(
                originalTimeoutMillis: Long,
                containsIcons: Boolean,
                containsText: Boolean,
                containsControls: Boolean
            ): Long {
                if (containsText && originalTimeoutMillis == 3_000L) {
                    return textTimeout
                }
                return originalTimeoutMillis
            }
        }
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        val activity = compose.activity
        compose.setContent {
            val configuration = android.content.res.Configuration(LocalConfiguration.current)
            configuration.fontScale = fontScale
            val displayDensity = LocalDensity.current.density
            CompositionLocalProvider(
                LocalAccessibilityManager provides accessibility,
                LocalActivityResultRegistryOwner provides activity,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(displayDensity, fontScale)
            ) {
                LocalizedContent(AppLanguage.English) {
                    PinGlideTheme {
                        primary = MaterialTheme.colorScheme.primary.toArgb()
                        density = LocalDensity.current.density
                        val insets = WindowInsets.safeDrawing.asPaddingValues()
                        val windowHeight = usableHeight + insets.calculateTopPadding() +
                            insets.calculateBottomPadding()
                        Box(Modifier.height(windowHeight)) {
                            AcceptedNavigation(
                                progress = CompletedSetup,
                                smsGranted = true,
                                onCheckSms = { true },
                                loginState = LoginState.FirstDevice,
                                home = fixture.home,
                                pairing = fixture.pairing,
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
        }
        return fixture
    }
}

private class DuplicateScanFixture(app: PinGlideApplication, scope: CoroutineScope) {
    private val now = Instant.now()
    private val targets = listOf(
        "remove" to "Remove me",
        "home" to "My computer",
        "office" to "Office computer"
    )
        .associate { (id, name) ->
            id to RemotePairing(id, "receiver-$id", name, Presence.Online, now.toString())
        }.toMutableMap()
    val store = HomePairingStore(targets.values.toList(), now.plusSeconds(3600).toString())
    private val renewal = CredentialRenewal(store, { _, _ -> error("Unexpected renewal") }, scope)
    private val pairings = OwnPairings(
        store,
        object : PairingService {
            override suspend fun list(managementToken: String) = targets.values.toList()

            override suspend fun remove(managementToken: String, pairingId: String) {
                checkNotNull(targets.remove(pairingId))
            }
        },
        renewal,
        scope
    )
    private val sessions = object : PairingSessionService {
        override suspend fun authorize(token: String, sessionId: String) =
            error("Duplicate scan must not authorize a session")

        override suspend fun cancel(token: String, sessionId: String) = error("Unexpected cancel")

        override fun watch(token: String, sessionId: String) = error("Unexpected session")
    }
    val home = HomeViewModel(pairings, app.senderRestoration()) { null }
    val pairing = PairingEntryViewModel(
        { entryScope ->
            PairingEntry(
                store,
                pairings,
                sessions,
                { PairingQr("session", it, ByteArray(0), ByteArray(0)) },
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

    init {
        runBlocking(Dispatchers.Main) {
            store.select(store.readPairings(), "office", false)
            pairings.refresh()
        }
    }
}

private object CompletedSetup : SetupProgress {
    override val state = SetupState(completed = true)
    override fun start() = error("Unexpected setup")
    override fun recordSmsRequest() = error("Unexpected permission request")
    override fun completeSmsPrerequisite() = error("Unexpected permission request")
    override fun acknowledgeBattery() = error("Unexpected battery setup")
    override fun complete() = Unit
}
