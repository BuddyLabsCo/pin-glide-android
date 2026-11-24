package com.buddylabs.pinglide.ui.pairing

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.domain.pairing.PairingAliasError
import com.buddylabs.pinglide.domain.pairing.PairingEntryPage
import com.buddylabs.pinglide.domain.pairing.PairingEntryProblem
import com.buddylabs.pinglide.domain.pairing.PairingEntryState
import com.buddylabs.pinglide.domain.pairing.PairingHost
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PairingConsentScreenTest {
    private val animatorScale = mutableFloatStateOf(1f)
    private val durationScale = object : MotionDurationScale {
        override val scaleFactor: Float get() = animatorScale.floatValue
    }

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>(effectContext = durationScale)

    private val state = mutableStateOf(
        PairingEntryState(
            page = PairingEntryPage.Consent,
            host = PairingHost.Onboarding,
            alias = "我的设备",
            remainingSeconds = 271
        )
    )
    private val actions = mutableListOf<String>()

    @Test
    fun firstDeviceShowsApprovedConsentAndSeparateActions() {
        render(AppLanguage.SimplifiedChinese)
        compose.onNodeWithText("连接新设备").assertIsDisplayed()
        compose.onNodeWithText(
            "确认后，目标设备会开始接收你选中的短信，双方会建立一条独立的加密配对。"
        ).assertIsDisplayed()
        compose.onNodeWithText("配对会话剩余时间").assertIsDisplayed()
        compose.onNodeWithText("04:31").assertIsDisplayed()
        val confirm = compose.onNodeWithText("确认并建立密钥").assertIsEnabled()
        val cancel = compose.onNodeWithText("取消").assertIsDisplayed()
        assertTrue(
            confirm.getUnclippedBoundsInRoot().bottom < cancel.getUnclippedBoundsInRoot().top
        )
        captureEvidence()
        confirm.performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        cancel.performClick()
        compose.runOnIdle { assertEquals(listOf("confirm", "back", "cancel"), actions) }
    }

    @Test
    fun aliasErrorsAndPendingPreventConfirmationAndHomeKeepsItsLabel() {
        render(AppLanguage.English)
        val alias = compose.onNodeWithContentDescription("Target name")
        val confirm = compose.onNodeWithText("Confirm and establish key")
        alias.performTextReplacement("")
        compose.runOnIdle {
            assertEquals("", state.value.alias)
            state.value = state.value.copy(aliasError = PairingAliasError.Blank)
        }
        compose.mainClock.advanceTimeBy(100)
        confirm.assertIsNotEnabled()
        compose.runOnIdle {
            state.value = state.value.copy(alias = "My device", aliasError = null, pending = true)
        }
        compose.mainClock.advanceTimeBy(100)
        alias.assertIsNotEnabled()
        compose.onNodeWithText("Validating Pairing session…").assertIsNotEnabled()
        compose.runOnIdle {
            state.value = state.value.copy(host = PairingHost.Home, pending = false)
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Confirm target").assertIsEnabled()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
    }

    @Test
    fun normalHeightKeepsActionsAnchoredWhileContentScrolls() {
        render(AppLanguage.English, usableHeight = 550.dp)
        compose.mainClock.autoAdvance = true
        val confirm = compose.onNodeWithText("Confirm and establish key")
        val cancel = compose.onNodeWithText("Cancel")
        val confirmBounds = confirm.assertIsDisplayed().getUnclippedBoundsInRoot()
        val cancelBounds = cancel.assertIsDisplayed().getUnclippedBoundsInRoot()
        val viewport = compose.onNodeWithTag("consent-viewport").getUnclippedBoundsInRoot()
        assertTrue(viewport.bottom - cancelBounds.bottom in 0.dp..48.dp)
        val title = compose.onNodeWithText("Connect a new device")
        val titleBounds = title.getUnclippedBoundsInRoot()

        compose.onNodeWithText(
            "Pairing session time remaining"
        ).performScrollTo().assertIsDisplayed()
        assertTrue(title.getUnclippedBoundsInRoot().top < titleBounds.top)
        assertEquals(confirmBounds, confirm.assertIsDisplayed().getUnclippedBoundsInRoot())
        assertEquals(cancelBounds, cancel.assertIsDisplayed().getUnclippedBoundsInRoot())
    }

    @Test
    fun compactHeightAtDoubleFontScaleKeepsAliasAndActionsReachable() {
        render(AppLanguage.English, usableHeight = 180.dp, fontScale = 2f)
        compose.mainClock.autoAdvance = true
        val alias = compose.onNodeWithContentDescription("Target name")
        alias.performScrollTo().assertIsDisplayed().performTextReplacement("My device")
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertEquals("My device", state.value.alias) }
        compose.onNodeWithText("Confirm and establish key").performScrollTo()
            .assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            state.value = state.value.copy(problem = PairingEntryProblem.Closed)
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Scan a new QR code").performScrollTo()
            .assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("rescan"), actions) }
    }

    @Test
    fun disablingMotionKeepsTheConsentIllustrationStill() {
        render(AppLanguage.SimplifiedChinese)
        val first = compose.onRoot().captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(250)
        assertFalse(first.sameAs(compose.onRoot().captureToImage().asAndroidBitmap()))
        compose.runOnIdle { animatorScale.floatValue = 0f }
        compose.mainClock.advanceTimeBy(250)
        val still = compose.onRoot().captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(250)
        assertTrue(still.sameAs(compose.onRoot().captureToImage().asAndroidBitmap()))
    }

    private fun render(language: AppLanguage, usableHeight: Dp? = null, fontScale: Float = 1f) {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LocalizedContent(language) {
                PinGlideTheme {
                    val density = LocalDensity.current.density
                    CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                        val insets = WindowInsets.safeDrawing.asPaddingValues()
                        val viewport = if (usableHeight == null) {
                            Modifier
                        } else {
                            Modifier.height(
                                usableHeight + insets.calculateTopPadding() +
                                    insets.calculateBottomPadding()
                            )
                        }
                        Box(viewport.testTag("consent-viewport")) {
                            PairingConsentScreen(
                                state.value,
                                onBack = { actions.add("back") },
                                onCancel = { actions.add("cancel") },
                                onAliasChange = { state.value = state.value.copy(alias = it) },
                                onConfirm = { actions.add("confirm") },
                                onRescan = { actions.add("rescan") }
                            )
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(300)
    }

    private fun captureEvidence() {
        if (InstrumentationRegistry.getArguments().getString("consentEvidence") != "true") return
        repeat(20) { frame ->
            compose.mainClock.advanceTimeBy(90)
            compose.waitForIdle()
            captureConsentEvidence("consent-%02d.png".format(frame))
        }
    }
}

internal fun captureConsentEvidence(filename: String) {
    if (InstrumentationRegistry.getArguments().getString("consentEvidence") != "true") return
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val directory = checkNotNull(instrumentation.targetContext.getExternalFilesDir(null))
    val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    File(directory, filename).outputStream().use {
        check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
    }
}
