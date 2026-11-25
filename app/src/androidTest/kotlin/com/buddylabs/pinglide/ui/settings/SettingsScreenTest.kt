package com.buddylabs.pinglide.ui.settings

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.platform.DeviceInfo
import com.buddylabs.pinglide.platform.permissions.SetupPermissions
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.components.captureSettledUiEvidence
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun approvedSettingsRowsDisplayInstalledVersionAndKeepTheirActions() {
        val context = compose.activity
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName!!
        val destinations = mutableListOf<SettingsDestination>()
        var batteryOpens = 0
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            LocalizedContent(AppLanguage.SimplifiedChinese) {
                PinGlideTheme {
                    SettingsScreen(
                        language = AppLanguage.System,
                        installedVersion = DeviceInfo(context).installedVersion(),
                        onBatterySettings = {
                            batteryOpens++
                            false
                        },
                        onNavigate = { destinations.add(it) },
                        onBack = {}
                    )
                }
            }
        }
        compose.onNodeWithText("隐私与数据").assertHasClickAction()
        compose.onNodeWithText(version).assertHasNoClickAction()
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("外观").assertHasNoClickAction()
        compose.onNodeWithText("QQ 账号").performScrollTo().assertIsDisplayed()
        assertApprovedGroups()
        listOf("QQ 账号", "语言", "设备访问管理", "隐私与数据").forEach { label ->
            compose.onNodeWithText(label).performScrollTo().performClick()
        }
        compose.onNodeWithText("电池优化").performScrollTo().performClick()
        compose.onNodeWithText("无法打开系统设置。请从设备设置中找到鹊信后重试。")
            .performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(
                listOf(
                    SettingsDestination.Account,
                    SettingsDestination.Language,
                    SettingsDestination.DeviceAccess,
                    SettingsDestination.Privacy
                ),
                destinations
            )
            assertEquals(1, batteryOpens)
        }
    }

    private fun assertApprovedGroups() {
        val context = compose.activity
        val container = ContextCompat.getColor(context, R.color.surface_container)
        val divider = ContextCompat.getColor(context, R.color.outline_variant)
        val iconTint = ContextCompat.getColor(context, R.color.on_surface_variant)
        val surface = ContextCompat.getColor(context, R.color.surface)
        val groups = listOf("账户" to 76, "显示" to 121, "后台运行" to 60, "安全" to 60, "关于" to 121)
        groups.forEach { (title, height) ->
            compose.onNodeWithText(title).performScrollTo().assertIsDisplayed()
            val group = compose.onNodeWithTag("settings-group:$title")
                .performScrollTo().assertIsDisplayed()
            val bounds = group.getUnclippedBoundsInRoot()
            assertTrue(
                "$title group height",
                abs((bounds.bottom - bounds.top).value - height) <= 1f
            )
            val image = group.captureToImage().asAndroidBitmap()
            assertEquals("$title outer corner", surface, image.getPixel(0, 0))
            val middleColor = if (height == 121) divider else container
            assertEquals(
                "$title contiguous surface",
                middleColor,
                image.getPixel(
                    4,
                    image.height / 2
                )
            )
            if (height == 76) {
                assertTrue("$title QQ badge", image.hasTint(15, 61, 38, 0xff0b7ac8.toInt()))
                assertTrue(
                    "$title approved chevron",
                    image.hasTint(
                        with(compose.density) { image.width.toDp().value.toInt() } - 35,
                        with(compose.density) { image.width.toDp().value.toInt() } - 15,
                        38,
                        Color.BLACK
                    )
                )
            } else {
                assertTrue("$title first row icon", image.hasTint(14, 34, 30, iconTint))
                if (height == 121) {
                    assertTrue("$title second row icon", image.hasTint(14, 34, 91, iconTint))
                }
            }
        }
    }

    private fun Bitmap.hasTint(left: Int, right: Int, center: Int, tint: Int): Boolean =
        with(compose.density) {
            val columns = left.dp.roundToPx() until right.dp.roundToPx()
            val rows = (center - 10).dp.roundToPx() until (center + 10).dp.roundToPx()
            rows.any { row -> columns.any { column -> getPixel(column, row) == tint } }
        }

    @Test
    fun compactEnglishWindowWithLargeFontsKeepsPrivacyAndBackReachable() {
        var destination: SettingsDestination? = null
        var back = false
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f)
            ) {
                Box(Modifier.height(300.dp)) {
                    LocalizedContent(AppLanguage.English) {
                        PinGlideTheme {
                            SettingsScreen(
                                language = AppLanguage.English,
                                installedVersion = "7.19.42-review",
                                onBatterySettings = { true },
                                onNavigate = { destination = it },
                                onBack = { back = true }
                            )
                        }
                    }
                }
            }
        }
        val backControl = compose.onNodeWithContentDescription("Back")
        val initialBackBounds = backControl.assertIsDisplayed().getUnclippedBoundsInRoot()
        compose.onNodeWithText("7.19.42-review").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Privacy and data").performScrollTo().assertIsDisplayed()
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(SettingsDestination.Privacy, destination) }
        assertEquals(initialBackBounds, backControl.assertIsDisplayed().getUnclippedBoundsInRoot())
        backControl.performTouchInput { click() }
        compose.runOnIdle { assertEquals(true, back) }
    }

    @Test
    fun settingsDialogRestoresLanguageFocusAndDispatchesSystemBatterySettingsIntent() {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        val activity = compose.activity
        val app = activity.application as PinGlideApplication
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(
            IntentFilter(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            ActivityResult(Activity.RESULT_OK, null),
            true
        )
        try {
            instrumentation.setInTouchMode(true)
            compose.setContent {
                LocalizedContent(AppLanguage.SimplifiedChinese) {
                    PinGlideTheme {
                        SettingsNavigation(
                            language = AppLanguage.System,
                            onLanguage = {},
                            onBatterySettings = SetupPermissions(activity)::openBatterySettings,
                            installedVersion = DeviceInfo(activity).installedVersion(),
                            account = app.accountSession,
                            senders = app.accountSenders,
                            revocation = app.senderRevocation,
                            onLogout = {},
                            onDismiss = {}
                        )
                    }
                }
            }
            captureSettledUiEvidence(
                "settings.png",
                compose.onNode(isRoot() and hasAnyDescendant(hasText("设置")))
            )
            val languageRow = compose.onNodeWithText("语言").assertIsDisplayed()
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_TAB)
            languageRow.performSemanticsAction(SemanticsActions.RequestFocus)
            languageRow.assertIsFocused()
            languageRow.performClick()
            compose.onNodeWithText("应用语言").assertIsDisplayed()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithText("语言").assertIsFocused()
            compose.onNodeWithText("电池优化").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(1, monitor.hits) }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.setInTouchMode(true)
        }
    }
}
