package com.buddylabs.pinglide.ui.home

import android.content.res.Configuration
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.data.service.TestDeliveryService
import com.buddylabs.pinglide.domain.pairing.PairingFailure
import com.buddylabs.pinglide.domain.pairing.PairingServiceException
import com.buddylabs.pinglide.domain.pairing.Presence
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.generated.http.model.UploadRequest
import com.buddylabs.pinglide.ui.home.PendingTargetTestMessageTest.TestMessageFeedbackEvents
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RejectedTargetTestMessageTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val inSheet = hasAnyAncestor(
        SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, TARGET)
    )
    private val uploads = ConcurrentLinkedQueue<UploadRequest>()
    private var inventoryReads = 0

    @After
    fun stopFixture() = scope.cancel()

    @Test
    fun lightDetailsShowsConfirmedRemovalAfterOneRejectedUpload() {
        assertRemovalOverlay(Configuration.UI_MODE_NIGHT_NO)
    }

    @Test
    fun darkDetailsShowsConfirmedRemovalAfterOneRejectedUpload() {
        assertRemovalOverlay(Configuration.UI_MODE_NIGHT_YES)
    }

    @Test
    fun activePairingKeepsTheSpecificAuthorizationError() {
        openTarget()
        assertInlineRejection("配对无效或已失去授权", expectedReads = 2)
    }

    @Test
    fun failedInventoryKeepsTheSpecificAuthorizationError() {
        openTarget(afterUpload = { throw PairingServiceException(PairingFailure.Unavailable) })
        assertInlineRejection("配对无效或已失去授权", expectedReads = 2)
    }

    @Test
    fun unknownRejectionUsesGenericCopyWithoutCheckingInventory() {
        openTarget(errorCode = "future_error")
        assertInlineRejection("测试消息未发送：投递被拒绝", expectedReads = 1)
    }

    @Test
    fun removalNoticeNamesTheTargetWhenTheTitleScrollsOutOfSight() {
        val originalSize = Regex("Override size: (\\d+x\\d+)").find(runHomeTestShell("wm size"))
            ?.groupValues?.get(1)
        val density = compose.activity.resources.displayMetrics.density
        try {
            runHomeTestShell(
                "wm size ${(366 * density).roundToInt()}x${(320 * density).roundToInt()}"
            )
            compose.waitUntil(5_000L) {
                compose.runOnUiThread {
                    compose.activity.resources.configuration.screenHeightDp <= 320
                }
            }
            openTarget(short = true, afterUpload = { it.drop(1) })
            val sheet = compose.onNode(inSheet and hasScrollAction())
            val title = sheetText(TARGET)
            val distance = with(compose.density) {
                (title.getUnclippedBoundsInRoot().bottom - sheet.getUnclippedBoundsInRoot().top)
                    .toPx()
            }
            sheet.performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                scroll(0f, distance + 4f)
            }
            assertTrue(
                title.getUnclippedBoundsInRoot().bottom <= sheet.getUnclippedBoundsInRoot().top
            )
            sheetText(SEND).performClick()
            awaitText("$TARGET：$REMOVED")
            assertSingleUpload(expectedReads = 2)
        } finally {
            runHomeTestShell("wm size ${originalSize ?: "reset"}")
        }
    }

    private fun assertRemovalOverlay(nightMode: Int) {
        openTarget(nightMode = nightMode, afterUpload = { it.drop(1) })
        val send = sheetText(SEND).assertIsEnabled()
        val rename = sheetText("重命名设备")
        val sendBounds = send.fetchSemanticsNode().boundsInWindow
        val renameBounds = rename.fetchSemanticsNode().boundsInWindow
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val feedback = TestMessageFeedbackEvents("$TARGET：$REMOVED")
        automation.setOnAccessibilityEventListener(feedback)
        try {
            send.performClick()
            awaitText(REMOVED)
            val visibleAt = SystemClock.uptimeMillis()
            send.assertIsEnabled()
            val noticeBounds = sheetText(REMOVED).fetchSemanticsNode().boundsInWindow
            assertEquals(sendBounds.center.x, noticeBounds.center.x, 1f)
            assertTrue(
                noticeBounds.top < renameBounds.top && noticeBounds.bottom > renameBounds.top
            )
            assertEquals(renameBounds, rename.fetchSemanticsNode().boundsInWindow)
            compose.waitUntil(3_000L) { feedback.status.isNotEmpty() }
            assertTrue(feedback.status.first().isVisibleToUser)
            compose.waitUntil(6_000L) { SystemClock.uptimeMillis() - visibleAt >= 4_250L }
            sheetText(REMOVED).assertIsDisplayed()
            compose.waitUntil(RECOMMENDED_TIMEOUT) {
                compose.onAllNodes(inSheet and hasText(REMOVED)).fetchSemanticsNodes().isEmpty()
            }
            assertEquals(1, feedback.status.size)
            assertTrue(feedback.announcementsOrToasts.isEmpty())
            assertSingleUpload(expectedReads = 2)
        } finally {
            automation.setOnAccessibilityEventListener(null)
        }
    }

    private fun assertInlineRejection(message: String, expectedReads: Int) {
        sheetText(SEND).performClick()
        awaitText("$TARGET：$message")
        sheetText(REMOVED).assertDoesNotExist()
        assertSingleUpload(expectedReads)
    }

    private fun assertSingleUpload(expectedReads: Int) {
        compose.runOnIdle {
            val item = uploads.single().items.single()
            assertEquals("home", item.pairingId)
            assertEquals("receiver-home", item.receiverId)
            assertEquals(expectedReads, inventoryReads)
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(12_000L) {
            compose.onAllNodes(inSheet and hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
        sheetText(text).assertIsDisplayed()
    }

    private fun openTarget(
        errorCode: String = "receiver_not_authorized",
        nightMode: Int = Configuration.UI_MODE_NIGHT_NO,
        short: Boolean = false,
        afterUpload: (List<RemotePairing>) -> List<RemotePairing> = { it }
    ) {
        val now = Instant.now().toString()
        renderReadyHome(
            compose,
            listOf(
                RemotePairing("home", "receiver-home", TARGET, Presence.Online, now),
                RemotePairing("office", "receiver-office", "办公室电脑", Presence.Offline, now)
            ),
            scope,
            nightMode = nightMode,
            onList = {
                inventoryReads++
                if (uploads.isEmpty()) it else afterUpload(it)
            },
            accessibilityManager = object : AccessibilityManager {
                override fun calculateRecommendedTimeoutMillis(
                    originalTimeoutMillis: Long,
                    containsIcons: Boolean,
                    containsText: Boolean,
                    containsControls: Boolean
                ): Long = if (containsText) RECOMMENDED_TIMEOUT else originalTimeoutMillis
            },
            testDelivery = TestDeliveryService("https://delivery.test", rejectedUpload(errorCode))
        ) { error("Unexpected Pairing removal") }
        if (short) compose.onNode(hasScrollAction()).performScrollToNode(hasText(TARGET))
        compose.onNodeWithText(TARGET).performClick()
        sheetText(TARGET).assertIsDisplayed()
    }

    private fun rejectedUpload(errorCode: String) = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        check(request.method == "POST" && request.url.encodedPath == "/v1/messages")
        val json = Buffer().also { checkNotNull(request.body).writeTo(it) }.readUtf8()
        val upload = Json.decodeFromString<UploadRequest>(json)
        uploads.add(upload)
        val response = """
            {"requestId":"request","messageId":"${upload.messageId}","results":[
              {"index":0,"receiverId":"receiver-home","status":"rejected",
               "error":{"code":"$errorCode","detail":"Rejected"}}
            ]}
        """.trimIndent()
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(207)
            .message("Multi-Status").body(response.toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private fun sheetText(text: String) = compose.onNode(inSheet and hasText(text))

    private companion object {
        const val TARGET = "我的电脑"
        const val SEND = "发送测试消息"
        const val REMOVED = "测试消息未发送：配对已移除"
        const val RECOMMENDED_TIMEOUT = 8_000L
    }
}
