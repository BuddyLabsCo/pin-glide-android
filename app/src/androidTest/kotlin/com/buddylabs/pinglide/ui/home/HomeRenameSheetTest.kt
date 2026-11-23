package com.buddylabs.pinglide.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
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
        text("别名只对当前设备可见").assertIsDisplayed()
        text("设备名称").assertIsDisplayed()
        text("不能为空，也不能与本机已配对的设备重名。").assertIsDisplayed()
        val cancel = text("取消").getUnclippedBoundsInRoot()
        val save = text("保存").getUnclippedBoundsInRoot()
        assertEquals(cancel.top, save.top)
        assertTrue(cancel.right < save.left)
        assertTrue(save.bottom - save.top >= 48.dp)

        val field = compose.onNode(hasSetTextAction())
        field.performTextReplacement("")
        text("请输入设备名称").assertIsDisplayed()
        text("保存").assertIsNotEnabled()
        field.performTextReplacement("a".repeat(41))
        text("设备名称不能超过 40 字").assertIsDisplayed()
        text("保存").assertIsNotEnabled()
        field.performTextReplacement("办公室电脑")
        text("已有一台同名设备，请换一个名称").assertIsDisplayed()
        text("保存").assertIsNotEnabled()
        text("取消").performClick()
        text("关闭").assertIsDisplayed()
    }

    @Test
    fun pendingSaveDisablesBothActionsAndAReportedFailureKeepsTheDraftForRetry() {
        val response = CompletableDeferred<Unit>()
        val requests = mutableListOf<Pair<String, String>>()
        openRename { id, alias ->
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
        retry.performClick()
        val success = text("设备名称已更新")
        compose.waitUntil(5_000) { success.isDisplayed() }
        text("关闭").assertIsDisplayed()
        assertEquals(2, compose.onAllNodesWithText("家用设备").fetchSemanticsNodes().size)
        text("关闭").performClick()
        text("家用设备").assertIsDisplayed()
        compose.runOnIdle { assertEquals(List(2) { "home" to "家用设备" }, requests) }
    }

    private fun openRename(
        onRename: suspend (String, String) -> Unit = { _, _ ->
            error("Unexpected rename")
        }
    ) {
        val now = Instant.now()
        renderReadyHome(
            compose,
            listOf(
                RemotePairing("home", "receiver-home", "我的电脑", Presence.Online, now.toString()),
                RemotePairing(
                    "office",
                    "receiver-office",
                    "办公室电脑",
                    Presence.Online,
                    now.minusSeconds(120).toString()
                )
            ),
            scope,
            onRename = onRename
        ) { error("Unexpected removal") }
        text("我的电脑").performClick()
        text("重命名设备").performClick()
    }

    private fun text(value: String) = compose.onNodeWithText(value)
}
