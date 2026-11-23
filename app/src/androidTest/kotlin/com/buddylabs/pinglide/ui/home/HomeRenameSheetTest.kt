package com.buddylabs.pinglide.ui.home

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.domain.pairing.PairingFailure
import com.buddylabs.pinglide.domain.pairing.PairingServiceException
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeRenameSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Main)

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun renameGroupsTheFieldAndKeepsBothActionsOnOneRowThroughValidation() {
        openRename()
        withImeVisibility { imeVisible ->
            compose.waitUntil(5_000, imeVisible)
            pressKey(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(5_000) { !imeVisible() }
        }
        text("别名只对当前设备可见").assertIsDisplayed()
        assertRenameLayout("不能为空，也不能与本机已配对的设备重名。")

        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("")
        assertRenameLayout("请输入设备名称")
        text("保存").assertIsNotEnabled()
        field.performTextReplacement("a".repeat(41))
        assertRenameLayout("设备名称不能超过 40 字")
        text("保存").assertIsNotEnabled()
        field.performTextReplacement("办公室电脑")
        assertRenameLayout("已有一台同名设备，请换一个名称")
        text("保存").assertIsNotEnabled()
        text("取消").performClick()
        text("关闭").assertIsDisplayed()
    }

    @Test
    fun pendingSaveDisablesBothActionsAndAReportedFailureKeepsTheDraftForRetry() {
        val response = CompletableDeferred<Unit>()
        val requests = mutableListOf<Pair<String, String>>()
        var reconciliationReads = 0
        openRename(onList = { items ->
            if (requests.isEmpty()) {
                items
            } else {
                reconciliationReads++
                items.map { item ->
                    if (item.pairingId == "office") target("office", "已同步的办公设备") else item
                }
            }
        }) { id, alias ->
            requests += id to alias
            if (requests.size == 1) {
                response.await()
                throw PairingServiceException(PairingFailure.Unavailable)
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("家用设备")
        text("保存").performClick()
        text("正在保存").assertIsNotEnabled()
        text("取消").assertIsNotEnabled()
        response.complete(Unit)

        val retry = text("重新保存")
        compose.waitUntil(5_000) { retry.isDisplayed() }
        text("家用设备").assertIsDisplayed()
        text("名称保存失败，输入已保留。请检查网络后重试；设备列表会核对实际保存结果。")
            .assertIsDisplayed()
        assertEquals(1, compose.runOnIdle { reconciliationReads })
        retry.performClick()
        awaitConfirmation()
        text("关闭").assertIsDisplayed()
        assertEquals(2, compose.onAllNodesWithText("家用设备").fetchSemanticsNodes().size)
        text("关闭").performClick()
        text("家用设备").assertIsDisplayed()
        text("已同步的办公设备").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, reconciliationReads)
            assertEquals(List(2) { "home" to "家用设备" }, requests)
        }
    }

    @Test
    fun duplicateIntroducedByAnInFlightRefreshRejectsSubmissionAndKeepsDraft() {
        val started = CompletableDeferred<Unit>()
        val refreshed = CompletableDeferred<List<RemotePairing>>()
        var listCalls = 0
        val home = openRename(onList = { initial ->
            if (++listCalls == 1) {
                initial
            } else {
                started.complete(Unit)
                refreshed.await()
            }
        })
        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("家用设备")
        compose.runOnIdle { home.refresh() }
        compose.waitUntil(5_000) { started.isCompleted }
        text("保存").assertIsEnabled().performClick()
        text("正在保存").assertIsNotEnabled()
        refreshed.complete(listOf(target("home", "我的电脑"), target("office", "家用设备")))

        val rejection = text("无法保存此名称。已有一台同名设备，请换一个名称")
        compose.waitUntil(5_000) { rejection.isDisplayed() }
        rejection.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
        compose.onNode(hasSetTextAction() and hasText("家用设备")).assertIsDisplayed()
        text("保存").assertIsNotEnabled()
        field.performTextReplacement("其他设备")
        rejection.assertDoesNotExist()
        text("保存").assertIsEnabled()
    }

    @Test
    fun typingStartsInTheFocusedAliasAndBackHidesTheKeyboardBeforeLeavingRename() {
        openRename()
        val field = compose.onNode(hasSetTextAction()).assertIsFocused()
        withImeVisibility { imeVisible ->
            compose.waitUntil(5_000, imeVisible)
            pressKey(KeyEvent.KEYCODE_X)
            compose.onNode(
                hasSetTextAction() and hasText("x", substring = true)
            ).assertIsDisplayed()
            pressKey(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(5_000) { !imeVisible() }
            field.assertIsDisplayed()
            text("保存").assertIsDisplayed()
            pressKey(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(5_000) { text("关闭").isDisplayed() }
            field.assertDoesNotExist()
        }
    }

    private fun withImeVisibility(check: (() -> Boolean) -> Unit) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        val originalFlags = info.flags
        info.flags = originalFlags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        try {
            check {
                automation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            }
        } finally {
            info.flags = originalFlags
            automation.serviceInfo = info
        }
    }

    private fun pressKey(keyCode: Int) =
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(keyCode)

    @Test
    fun confirmedRenameKeepsLargeTextActionsVisibleAndAnnouncesOnTheHeading() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val original = Settings.System.getFloat(resolver, Settings.System.FONT_SCALE, 1f)
        try {
            setFontScale(2f)
            verifyConfirmedRename()
        } finally {
            setFontScale(original)
        }
    }

    private fun setFontScale(scale: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val descriptor = instrumentation.uiAutomation.executeShellCommand(
            "settings put system font_scale $scale"
        )
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        compose.waitUntil(5_000) {
            compose.runOnUiThread { compose.activity.resources.configuration.fontScale == scale }
        }
    }

    private fun verifyConfirmedRename() {
        openRename { _, _ -> }
        compose.onNode(hasSetTextAction()).performTextReplacement("家用设备")
        text("保存").performScrollTo().performClick()
        awaitConfirmation()
        text("家用设备", inSheet = true).assertIsDisplayed()
        text("设备名称已更新").assertDoesNotExist()
        val labels = listOf("接收短信", "发送测试消息", "重命名设备", "移除这台设备的配对", "关闭")
        val bounds = labels.map {
            text(it, inSheet = true).assertIsDisplayed().getUnclippedBoundsInRoot()
        }
        assertTrue(bounds.zipWithNext().all { (above, below) -> above.bottom <= below.top })
    }

    private fun awaitConfirmation() {
        val confirmation = compose.onNode(hasStateDescription("设备名称已更新"))
        compose.waitUntil(5_000) { confirmation.isDisplayed() }
        confirmation.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
        compose.onAllNodesWithText("家用设备").assertCountEquals(2)
    }

    private fun assertRenameLayout(hint: String) {
        val groupTag = "renameAliasGroup"
        val group = compose.onNodeWithTag(groupTag).getUnclippedBoundsInRoot()
        val members = listOf(hasText("设备名称"), hasSetTextAction(), hasText(hint))
        val bounds = members.map { member ->
            compose.onNode(member and hasAnyAncestor(hasTestTag(groupTag)))
                .assertIsDisplayed().getUnclippedBoundsInRoot()
        }
        assertTrue(
            "Group bounds $group should contain $bounds",
            bounds.all {
                it.left >= group.left && it.right <= group.right &&
                    it.top >= group.top && it.bottom <= group.bottom
            }
        )
        assertTrue(bounds.zipWithNext().all { (above, below) -> above.bottom <= below.top })
        val cancel = text("取消").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val save = text("保存").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(cancel.top, save.top)
        assertTrue(cancel.right < save.left)
        assertTrue(cancel.height >= 48 * compose.density.density)
        assertTrue(save.height >= 48 * compose.density.density)
    }

    private fun openRename(
        onList: suspend (List<RemotePairing>) -> List<RemotePairing> = { it },
        onRename: suspend (String, String) -> Unit = { _, _ ->
            error("Unexpected rename")
        }
    ): HomeViewModel {
        val home = renderReadyHome(
            compose,
            listOf(target("home", "我的电脑"), target("office", "办公室电脑")),
            scope,
            onList = onList,
            onRename = onRename
        ) { error("Unexpected removal") }
        text("我的电脑").performClick()
        text("重命名设备").performClick()
        return home
    }

    private fun target(id: String, alias: String) =
        RemotePairing(id, "receiver-$id", alias, Presence.Online, Instant.now().toString())

    private fun text(value: String, inSheet: Boolean = false) = if (inSheet) {
        compose.onNode(hasText(value) and hasAnyAncestor(isDialog()))
    } else {
        compose.onNodeWithText(value)
    }
}
