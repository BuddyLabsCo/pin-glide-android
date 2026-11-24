package com.buddylabs.pinglide.ui.home

import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.forwarding.PairingTestMessage
import com.buddylabs.pinglide.domain.forwarding.TestDeliveryService
import com.buddylabs.pinglide.domain.forwarding.TestMessageOutcome
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.pairing.OwnPairings
import com.buddylabs.pinglide.domain.pairing.PairingService
import com.buddylabs.pinglide.domain.pairing.RemotePairing
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.generated.http.model.UploadRequest
import com.buddylabs.pinglide.platform.crypto.encryptTestMessage
import com.buddylabs.pinglide.ui.components.AnchoredPairingTestAction
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.components.TestMessageAction
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer

internal fun runHomeTestShell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
    InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
).bufferedReader().use { it.readText() }

internal fun textTimeoutAccessibility(timeoutMillis: Long): AccessibilityManager =
    object : AccessibilityManager {
        override fun calculateRecommendedTimeoutMillis(
            originalTimeoutMillis: Long,
            containsIcons: Boolean,
            containsText: Boolean,
            containsControls: Boolean
        ): Long = if (containsText) timeoutMillis else originalTimeoutMillis
    }

@OptIn(ExperimentalCoroutinesApi::class)
internal fun assertNoticeExpiresAtRecommendedTimeout(
    compose: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
    outcome: TestMessageOutcome,
    expectedCopy: String,
    timeoutMillis: Long
) = runTest {
    val action = TestMessageAction(this) { outcome }
    val accessibility = textTimeoutAccessibility(timeoutMillis)
    val sendLabel = "发送测试消息"
    action.enter("home")
    compose.setContent {
        LocalizedContent(AppLanguage.SimplifiedChinese) {
            PinGlideTheme {
                AnchoredPairingTestAction(action, accessibility, true, 0f) { _, send, divider ->
                    Column {
                        Button(onClick = send) { Text(sendLabel) }
                        HorizontalDivider(divider)
                    }
                }
            }
        }
    }
    compose.onNodeWithText(sendLabel).performClick()
    runCurrent()
    val notice = compose.onNodeWithText(expectedCopy)
    notice.assertIsDisplayed()
    advanceTimeBy(timeoutMillis - 1)
    runCurrent()
    notice.assertIsDisplayed()
    advanceTimeBy(1)
    runCurrent()
    notice.assertDoesNotExist()
}

internal fun testMessageUploadClient(responseBody: (UploadRequest) -> String): OkHttpClient =
    OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        check(request.method == "POST" && request.url.encodedPath == "/v1/messages")
        val json = Buffer().also { checkNotNull(request.body).writeTo(it) }.readUtf8()
        val upload = Json.decodeFromString<UploadRequest>(json)
        val body = responseBody(upload).toResponseBody("application/json".toMediaType())
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(207)
            .message("Multi-Status").body(body).build()
    }.build()

internal fun renderReadyHome(
    compose: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
    targets: List<RemotePairing>,
    scope: CoroutineScope,
    language: AppLanguage = AppLanguage.SimplifiedChinese,
    nightMode: Int? = null,
    onList: suspend (List<RemotePairing>) -> List<RemotePairing> = { it },
    onRename: suspend (String, String) -> Unit = { _, _ -> error("Unexpected rename") },
    sendTest: suspend (String) -> TestMessageOutcome? = { null },
    missingKeyId: String? = null,
    accessibilityManager: AccessibilityManager? = null,
    testDelivery: TestDeliveryService? = null,
    onRemove: suspend (String) -> Unit
): HomeViewModel {
    val store = HomePairingStore(
        targets,
        Instant.now().plusSeconds(3600).toString(),
        missingKeyId = missingKeyId
    )
    val renewal = CredentialRenewal(store, { _, _ -> error("Unexpected renewal") }, scope)
    val service = object : PairingService {
        private val remote = targets.associateBy { it.pairingId }.toMutableMap()

        override suspend fun list(managementToken: String) = onList(remote.values.toList())

        override suspend fun rename(
            managementToken: String,
            pairingId: String,
            alias: String
        ): RemotePairing {
            onRename(pairingId, alias)
            val target = remote.getValue(pairingId)
            return RemotePairing(
                pairingId,
                target.receiverId,
                alias,
                target.presence,
                target.stateChangedAt
            )
                .also { remote[pairingId] = it }
        }

        override suspend fun remove(managementToken: String, pairingId: String) {
            onRemove(pairingId)
            checkNotNull(remote.remove(pairingId))
        }
    }
    val pairings = OwnPairings(store, service, renewal, scope)
    runBlocking(Dispatchers.Main) { pairings.refresh() }
    compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
    val app = compose.activity.application as PinGlideApplication
    val message = testDelivery?.let {
        PairingTestMessage(store, renewal, ::encryptTestMessage, it)
    }
    val home = HomeViewModel(
        pairings,
        app.senderRestoration(),
        if (message == null) sendTest else { pairingId -> message.sendFromHome(pairingId, service) }
    )
    compose.setContent {
        val state by home.state.collectAsState()
        val systemConfiguration = LocalConfiguration.current
        val configuration = Configuration(systemConfiguration).apply {
            if (nightMode != null) {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            }
        }
        val accessibility = LocalAccessibilityManager.current
        CompositionLocalProvider(
            LocalConfiguration provides configuration,
            LocalAccessibilityManager provides (accessibilityManager ?: accessibility)
        ) {
            LocalizedContent(language) {
                PinGlideTheme {
                    HomeScreen(
                        state,
                        smsGranted = true,
                        smsRecovery = SmsRecovery.Idle,
                        home = home,
                        onRestoreSms = {},
                        onAdd = {},
                        onSettings = {}
                    )
                }
            }
        }
    }
    return home
}
