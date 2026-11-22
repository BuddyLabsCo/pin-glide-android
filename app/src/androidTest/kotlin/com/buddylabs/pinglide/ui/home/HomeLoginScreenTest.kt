package com.buddylabs.pinglide.ui.home

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
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
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeLoginScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun failedRestorationShowsPairingRetentionAndRetryInSimplifiedChinese() {
        verifyFailedRestoration(
            AppLanguage.SimplifiedChinese,
            "暂时无法登录",
            "网络或服务暂时不可用。原有配对仍保留，请稍后重新登录。",
            "使用 QQ 重新登录"
        )
    }

    @Test
    fun failedRestorationShowsPairingRetentionAndRetryInEnglish() {
        verifyFailedRestoration(
            AppLanguage.English,
            "Unable to sign in right now",
            "The network or service is temporarily unavailable. " +
                "Your existing Pairings are retained. Try signing in again later.",
            "Sign in again with QQ"
        )
    }

    private fun verifyFailedRestoration(
        language: AppLanguage,
        title: String,
        approvedCopy: String,
        retryLabel: String
    ) {
        var retries = 0
        compose.setContent {
            LocalizedContent(language) {
                PinGlideTheme {
                    HomeLoginScreen(
                        state = RestorationState.Error,
                        onRetry = { retries++ },
                        onCancel = { error("Failed restoration has no pending QQ authorization") },
                        onBack = { error("Retry must stay in the restoration flow") }
                    )
                }
            }
        }

        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(approvedCopy).assertIsDisplayed()
        compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        ).assertDoesNotExist()
        val action = compose.onNodeWithText(retryLabel)
        action.assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.assertAccountActionIcon(action, retryLabel, context.getColor(R.color.on_primary))
        action.performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun pendingRestorationShowsProgressAndCancelStopsQqAuthorization() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = checkNotNull(context.applicationContext as? PinGlideApplication)
        val resources = context.resources
        val waitingForQq = CompletableDeferred<Unit>()
        val authorizationCancelled = CompletableDeferred<Unit>()
        val renewalScope = CoroutineScope(Dispatchers.Main)
        val restoration = SenderRestoration(
            store = LoginRequiredStore,
            authorization = {
                waitingForQq.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    authorizationCancelled.complete(Unit)
                }
            },
            service = { _, _, _ -> error("QQ authorization is still pending") },
            renewal = CredentialRenewal(
                LoginRequiredStore,
                { _, _ -> error("Restoration requires QQ authorization") },
                renewalScope
            ),
            deviceName = { "Test Sender" }
        )
        val home = HomeViewModel(app.ownPairings, restoration) { null }
        try {
            compose.setContent {
                val state by home.loginRestoration.collectAsState()
                LaunchedEffect(Unit) { home.restoreLogin() }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    PinGlideTheme {
                        HomeLoginScreen(
                            state,
                            home::restoreLogin,
                            home::cancelLogin,
                            home::leaveLogin
                        )
                    }
                }
            }

            compose.waitUntil { waitingForQq.isCompleted }
            compose.onNodeWithText(
                resources.getString(R.string.home_login_loading)
            ).assertIsDisplayed()
            compose.onNodeWithText(
                resources.getString(R.string.home_login_loading_copy)
            ).assertIsDisplayed()
            compose.onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate
                )
            ).assertWidthIsEqualTo(196.dp)
            compose.onNodeWithText(resources.getString(R.string.setup_cancel)).performClick()

            compose.waitUntil { authorizationCancelled.isCompleted }
            assertEquals(RestorationState.Cancelled, home.loginRestoration.value)
            val heading = compose.onNodeWithText(resources.getString(R.string.home_login_cancelled))
                .assertIsDisplayed()
            assertCancelledEmblem(heading)
            val headingLayouts = mutableListOf<TextLayoutResult>()
            heading.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(headingLayouts)
            }
            val headingLayout = headingLayouts.single().layoutInput
            assertEquals(26.sp, headingLayout.style.fontSize)
            assertEquals(FontWeight(750), headingLayout.style.fontWeight)
            compose.onNodeWithText(
                resources.getString(R.string.home_login_cancelled_copy)
            ).assertIsDisplayed()
            val retry = compose.onNodeWithText(resources.getString(R.string.home_login_action))
                .assertIsDisplayed()
                .assertHeightIsEqualTo(48.dp)
            assertRetryIcon(retry, context, headingLayout.layoutDirection)
            retry.performClick()
            compose.onNodeWithText(
                resources.getString(R.string.home_login_loading)
            ).assertIsDisplayed()
        } finally {
            home.viewModelScope.cancel()
            renewalScope.cancel()
        }
    }

    private fun assertCancelledEmblem(heading: SemanticsNodeInteraction) = with(compose.density) {
        val root = compose.onRoot()
        val artwork = root.captureToImage().asAndroidBitmap()
        val headingTop = heading.getUnclippedBoundsInRoot().top
        val rootTop = root.getUnclippedBoundsInRoot().top
        val centerY = (headingTop - rootTop - 115.dp).roundToPx()
        val centerX = artwork.width / 2
        val background = artwork.getPixel(centerX - 102.dp.roundToPx(), centerY)
        val backplateEdge = centerY - 69.dp.roundToPx()
        assertTrue(
            "The backplate is visible above the QQ tile",
            artwork.getPixel(centerX, backplateEdge) != background
        )
        assertEquals(
            "The rounded backplate leaves its corner clear",
            background,
            artwork.getPixel(centerX - 69.dp.roundToPx(), backplateEdge)
        )
        val ringTolerance = 2.dp.roundToPx()
        for (radius in listOf(93.dp, 111.dp)) {
            val ringX = centerX - radius.roundToPx()
            assertTrue(
                "The $radius ring is visible around the QQ emblem",
                (ringX - ringTolerance..ringX + ringTolerance).any {
                    artwork.getPixel(it, centerY) != background
                }
            )
        }
    }

    private fun assertRetryIcon(
        action: SemanticsNodeInteraction,
        context: Context,
        layoutDirection: LayoutDirection
    ) = with(compose.density) {
        val label = compose.onNodeWithText(
            context.getString(R.string.home_login_action),
            useUnmergedTree = true
        ).getUnclippedBoundsInRoot()
        val actionLeft = action.getUnclippedBoundsInRoot().left
        val iconCenter = if (layoutDirection == LayoutDirection.Rtl) {
            label.right + 18.dp
        } else {
            label.left - 18.dp
        }
        val sampleX = (iconCenter - actionLeft).roundToPx()
        val button = action.captureToImage().asAndroidBitmap()
        val centerY = button.height / 2
        val buttonColor = context.getColor(R.color.primary)
        assertEquals(
            "The account icon has a filled rim",
            context.getColor(R.color.on_primary),
            button.getPixel(sampleX, centerY - 6.dp.roundToPx())
        )
        assertEquals(
            "The account icon has a head cutout",
            buttonColor,
            button.getPixel(sampleX, centerY - 2.dp.roundToPx())
        )
        assertEquals(
            "The account icon has a shoulders cutout",
            buttonColor,
            button.getPixel(sampleX, centerY + 4.dp.roundToPx())
        )
    }
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
