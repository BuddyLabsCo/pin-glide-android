package com.buddylabs.pinglide.ui.home

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import com.buddylabs.pinglide.domain.identity.IdentityStore
import com.buddylabs.pinglide.domain.identity.LoginAttempt
import com.buddylabs.pinglide.domain.identity.ManagementCredential
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.RefreshState
import com.buddylabs.pinglide.domain.identity.RestorationState
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.identity.SenderRestoration
import com.buddylabs.pinglide.domain.identity.UploadCredential
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeLoginScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pendingRestorationShowsHomeProgressAndCancel() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val waitingForQq = CompletableDeferred<Unit>()
        val restoration = SenderRestoration(
            store = LoginRequiredStore,
            authorization = {
                waitingForQq.complete(Unit)
                awaitCancellation()
            },
            service = { _, _, _ -> error("QQ authorization is still pending") },
            renewal = CredentialRenewal(
                LoginRequiredStore,
                { _, _ -> error("Restoration requires QQ authorization") },
                CoroutineScope(waitingForQq)
            ),
            deviceName = { "Test Sender" }
        )
        compose.setContent {
            val state by restoration.state.collectAsState()
            LaunchedEffect(Unit) { restoration.restore() }
            PinGlideTheme {
                HomeLoginScreen(state, onRetry = {}, onCancel = restoration::cancel, onBack = {})
            }
        }

        compose.waitUntil { waitingForQq.isCompleted }
        compose.onNodeWithText(resources.getString(R.string.home_login_loading)).assertIsDisplayed()
        compose.onNodeWithText(resources.getString(R.string.home_login_loading_copy))
            .assertIsDisplayed()
        compose.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo.Indeterminate
            )
        ).assertWidthIsEqualTo(196.dp)
        compose.onNodeWithText(resources.getString(R.string.setup_cancel)).performClick()

        assertEquals(RestorationState.Cancelled, restoration.state.value)
        compose.onNodeWithText(
            resources.getString(R.string.home_login_cancelled)
        ).assertIsDisplayed()
    }

    private object LoginRequiredStore : IdentityStore {
        override suspend fun read(): IdentitySnapshot = IdentitySnapshot(
            identity = SenderIdentity(
                "account",
                "sender",
                ManagementCredential("management", EXPIRY),
                UploadCredential("upload", EXPIRY),
                RefreshCredential("refresh", EXPIRY)
            ),
            refreshState = RefreshState.LoginRequired
        )

        override suspend fun updateRenewal(
            expected: IdentitySnapshot,
            replacement: IdentitySnapshot,
            attempt: LoginAttempt
        ): Boolean = error("Pending restoration must not update credentials")

        override suspend fun accept(
            expectedGeneration: Long,
            identity: SenderIdentity,
            attempt: LoginAttempt
        ): Boolean = error("Pending restoration must not replace the Sender")

        private const val EXPIRY = "2099-01-01T00:00:00Z"
    }
}
