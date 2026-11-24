package com.buddylabs.pinglide.ui.pairing

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.domain.pairing.PairingAliasError
import com.buddylabs.pinglide.domain.pairing.PairingEntryPage
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
        compose.runOnIdle {
            state.value = state.value.copy(host = PairingHost.Home, pending = false)
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Confirm target").assertIsEnabled()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
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

    private fun render(language: AppLanguage) {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LocalizedContent(language) {
                PinGlideTheme {
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
        compose.mainClock.advanceTimeBy(300)
    }

    private fun captureEvidence() {
        if (InstrumentationRegistry.getArguments().getString("consentEvidence") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = checkNotNull(instrumentation.targetContext.getExternalFilesDir(null))
        repeat(20) { frame ->
            compose.mainClock.advanceTimeBy(90)
            compose.waitForIdle()
            val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(directory, "consent-%02d.png".format(frame)).outputStream().use {
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
    }
}
