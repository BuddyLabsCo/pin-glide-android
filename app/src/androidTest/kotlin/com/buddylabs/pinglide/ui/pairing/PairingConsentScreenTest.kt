package com.buddylabs.pinglide.ui.pairing

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.view.KeyEvent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.domain.pairing.PairingAliasError
import com.buddylabs.pinglide.domain.pairing.PairingEntryPage
import com.buddylabs.pinglide.domain.pairing.PairingEntryProblem
import com.buddylabs.pinglide.domain.pairing.PairingEntryState
import com.buddylabs.pinglide.domain.pairing.PairingHost
import com.buddylabs.pinglide.domain.pairing.pairingAliasError
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.components.captureUiEvidence
import com.buddylabs.pinglide.ui.components.uiEvidenceEnabled
import kotlin.math.abs
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
    fun blankTargetNameShowsAccessibleErrorAndDisablesConfirmation() {
        verifyBlankName(Configuration.UI_MODE_NIGHT_NO, 0xFF9AC4C0.toInt(), 0xFFFBFDFB.toInt())
        captureUiEvidence("consent-blank.png")
    }

    @Test
    fun darkBlankTargetNameShowsApprovedDisabledConfirmation() {
        verifyBlankName(Configuration.UI_MODE_NIGHT_YES, 0xFF346059.toInt(), 0xFF072320.toInt())
        captureUiEvidence("consent-blank-dark.png")
    }

    private fun verifyBlankName(nightMode: Int, expectedFill: Int, expectedContent: Int) {
        state.value = state.value.copy(
            host = PairingHost.Home,
            alias = "",
            aliasError = PairingAliasError.Blank
        )
        render(AppLanguage.SimplifiedChinese, nightMode = nightMode)
        val alias = compose.onNodeWithContentDescription("设备名称")
        val error = compose.onNodeWithText("请输入设备名称")
        alias.assertIsDisplayed().assertIsEnabled().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, "请输入设备名称")
        )
        error.assertIsDisplayed()
        assertTrue(alias.getUnclippedBoundsInRoot().bottom < error.getUnclippedBoundsInRoot().top)
        val confirm = compose.onNodeWithText("确认添加设备").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsDisplayed().assertIsEnabled()
        val button = confirm.captureToImage().asAndroidBitmap()
        val fill = button.getPixel(button.width / 4, button.height / 2)
        assertTrue(
            "Disabled fill: expected ${expectedFill.toUInt().toString(16)}, " +
                "got ${fill.toUInt().toString(16)}",
            matchesColor(expectedFill, fill)
        )
        val width = button.width / 2
        val height = button.height / 2
        val pixels = IntArray(width * height)
        button.getPixels(pixels, 0, width, button.width / 4, button.height / 4, width, height)
        assertTrue(
            "Disabled content should retain the muted on-primary color",
            pixels.count { matchesColor(expectedContent, it) } >= 4
        )
    }

    private fun matchesColor(expected: Int, actual: Int): Boolean =
        abs(AndroidColor.red(expected) - AndroidColor.red(actual)) <= 2 &&
            abs(AndroidColor.green(expected) - AndroidColor.green(actual)) <= 2 &&
            abs(AndroidColor.blue(expected) - AndroidColor.blue(actual)) <= 2

    @Test
    fun aliasErrorsAndPendingPreventConfirmationAndHomeKeepsItsLabel() {
        render(AppLanguage.English, otherAliases = listOf("Office PC"))
        compose.mainClock.autoAdvance = true
        val alias = compose.onNodeWithContentDescription("Target name")
        val confirm = compose.onNodeWithText("Confirm and establish key")
        alias.performTextReplacement("")
        compose.runOnIdle {
            assertEquals("", state.value.alias)
        }
        compose.mainClock.advanceTimeBy(100)
        confirm.assertIsNotEnabled()
        alias.performTextReplacement(" office pc ")
        compose.mainClock.advanceTimeBy(100)
        val guidance = "Another paired target already has this name. Choose a different name."
        compose.onNodeWithText(guidance).assertExists()
        alias.assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, guidance))
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
    fun duplicateNameMarksEditableFieldAndDisablesConfirmUntilCorrected() {
        val existing = "办公室电脑"
        state.value = state.value.copy(
            host = PairingHost.Home,
            alias = existing,
            aliasError = PairingAliasError.Duplicate
        )
        render(AppLanguage.SimplifiedChinese, otherAliases = listOf(existing))
        val guidance = "已有一台同名设备，请换一个名称"
        val alias = compose.onNodeWithContentDescription("设备名称")
        val confirm = compose.onNodeWithText("确认添加设备")
        alias.assertIsDisplayed().assertIsEnabled().assertTextContains(existing)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, guidance))
        compose.onNodeWithText(guidance).assertIsDisplayed()
        confirm.assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsEnabled()
        captureUiEvidence("duplicate-consent.png")
        val button = confirm.captureToImage().asAndroidBitmap()
        val context = compose.activity
        val expected = Color(ContextCompat.getColor(context, R.color.primary)).copy(alpha = .38f)
            .compositeOver(Color(ContextCompat.getColor(context, R.color.surface)))
        val actual = Color(button.getPixel(button.width / 6, button.height / 2))
        assertTrue(
            "Expected faded primary $expected, got $actual",
            listOf(
                abs(expected.red - actual.red),
                abs(expected.green - actual.green),
                abs(expected.blue - actual.blue)
            ).all { it <= 1f / 255f }
        )
        alias.performTextReplacement("书房设备")
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText(guidance).assertDoesNotExist()
        confirm.assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("confirm"), actions) }
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
        val decor = compose.activity.window.decorView
        val alias = compose.onNodeWithContentDescription("Target name")
        alias.performScrollTo().performTouchInput { click() }
        compose.waitUntil(5_000) {
            ViewCompat.getRootWindowInsets(decor)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        alias.performScrollTo().assertIsDisplayed().performTextReplacement("My device")
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertEquals("My device", state.value.alias) }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) {
            ViewCompat.getRootWindowInsets(decor)?.isVisible(WindowInsetsCompat.Type.ime()) == false
        }
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

    private fun render(
        language: AppLanguage,
        usableHeight: Dp? = null,
        fontScale: Float = 1f,
        nightMode: Int? = null,
        otherAliases: List<String> = emptyList()
    ) {
        val configuration = Configuration(compose.activity.resources.configuration).apply {
            if (nightMode != null) {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            }
        }
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalConfiguration provides configuration,
                LocalDensity provides Density(density, fontScale)
            ) {
                LocalizedContent(language) {
                    PinGlideTheme {
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
                                onAliasChange = {
                                    state.value = state.value.copy(
                                        alias = it,
                                        aliasError = pairingAliasError(it, otherAliases)
                                    )
                                },
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
        if (!uiEvidenceEnabled()) return
        repeat(20) { frame ->
            compose.mainClock.advanceTimeBy(90)
            compose.waitForIdle()
            captureUiEvidence("consent-%02d.png".format(frame))
        }
    }
}
