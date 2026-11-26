package com.buddylabs.pinglide.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.identity.AccountService
import com.buddylabs.pinglide.domain.identity.AccountSession
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.LogoutStore
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import com.buddylabs.pinglide.ui.home.HomePairingStore
import java.time.Instant
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.assertTrue

internal class AccountScreenFixture(
    private val compose: AndroidComposeTestRule<*, ComponentActivity>
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val responses = Channel<Result<String?>>(Channel.UNLIMITED)
    private var failOnDisposal = false
    var logoutCount = 0
        private set
    var readCount = 0
        private set

    fun close() {
        scope.cancel()
        responses.close()
    }

    fun openAccount(
        language: AppLanguage = AppLanguage.SimplifiedChinese,
        restoration: StateRestorationTester? = null
    ) {
        val app = compose.activity.application as PinGlideApplication
        val store = HomePairingStore(emptyList(), Instant.now().plusSeconds(3600).toString())
        val renewal = CredentialRenewal(store, { _, _ -> error("Unexpected renewal") }, scope)
        val account = AccountSession(
            object : LogoutStore {
                override suspend fun logout(): RefreshCredential? = error("Unexpected logout")
            },
            renewal,
            object : AccountService {
                override suspend fun profile(identity: SenderIdentity): String? {
                    readCount++
                    return responses.receive().getOrThrow()
                }

                override suspend fun logout(credential: RefreshCredential): Unit =
                    error("Unexpected service logout")
            },
            scope
        )
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        val content: @Composable () -> Unit = {
            DisposableEffect(Unit) {
                onDispose {
                    if (failOnDisposal) {
                        failOnDisposal = false
                        check(account.state.value.loading)
                        sendFailure()
                        check(account.state.value.failed)
                    }
                }
            }
            LocalizedContent(language) {
                PinGlideTheme {
                    SettingsNavigation(
                        language = language,
                        onLanguage = {},
                        onBatterySettings = { false },
                        installedVersion = "0.1.0",
                        account = account,
                        senders = app.accountSenders,
                        revocation = app.senderRevocation,
                        onLogout = { logoutCount++ },
                        onDismiss = {}
                    )
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        val label = if (language == AppLanguage.English) "QQ account" else "QQ 账号"
        compose.onNodeWithText(label).performScrollTo().performClick()
    }

    fun respond(nickname: String) {
        check(responses.trySend(Result.success(nickname)).isSuccess)
        compose.waitUntil(5_000) { compose.onNodeWithText(nickname).isDisplayed() }
    }

    fun fail(message: String) {
        sendFailure()
        compose.waitUntil(5_000) { compose.onNodeWithText(message).isDisplayed() }
    }

    fun failDuringRestoration() {
        compose.runOnIdle { failOnDisposal = true }
    }

    private fun sendFailure() {
        check(
            responses.trySend(
                Result.failure(IllegalStateException("Profile unavailable"))
            ).isSuccess
        )
    }
}

internal fun AndroidComposeTestRule<*, ComponentActivity>.textLayout(
    text: String
): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    onNodeWithText(text, useUnmergedTree = true)
        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
    return results.single()
}

internal fun assertDp(label: String, actual: Dp, expected: Dp) {
    assertTrue("$label: $actual", abs((actual - expected).value) <= 2f)
}
