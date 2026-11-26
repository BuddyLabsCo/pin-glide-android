package com.buddylabs.pinglide.ui.settings

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.buddylabs.pinglide.app.LocalizedContent
import com.buddylabs.pinglide.app.PinGlideApplication
import com.buddylabs.pinglide.domain.identity.AccountSender
import com.buddylabs.pinglide.domain.identity.AccountSenders
import com.buddylabs.pinglide.domain.identity.CredentialRenewal
import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import com.buddylabs.pinglide.domain.identity.IdentityStore
import com.buddylabs.pinglide.domain.identity.LoginAttempt
import com.buddylabs.pinglide.domain.identity.ManagementCredential
import com.buddylabs.pinglide.domain.identity.QqAuthorization
import com.buddylabs.pinglide.domain.identity.RefreshCredential
import com.buddylabs.pinglide.domain.identity.SenderIdentity
import com.buddylabs.pinglide.domain.identity.SenderRevocation
import com.buddylabs.pinglide.domain.identity.SenderRevocationService
import com.buddylabs.pinglide.domain.identity.UploadCredential
import com.buddylabs.pinglide.domain.settings.AppLanguage
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import kotlinx.coroutines.CoroutineScope

internal fun AndroidComposeTestRule<*, ComponentActivity>.renderRevocationInventory(
    scope: CoroutineScope,
    language: AppLanguage,
    authorization: QqAuthorization = { error("Confirmation must not open QQ") },
    service: SenderRevocationService = { _, _, _ ->
        error("Confirmation must not submit revocation")
    }
) {
    val expiry = "2099-01-01T00:00:00Z"
    val identity = SenderIdentity(
        "example-account",
        "example-sender",
        ManagementCredential("example-management", expiry),
        UploadCredential("example-upload", expiry),
        RefreshCredential("example-refresh", expiry)
    )
    val store = object : IdentityStore {
        override suspend fun read() = IdentitySnapshot(identity = identity)

        override suspend fun updateRenewal(
            expected: IdentitySnapshot,
            replacement: IdentitySnapshot,
            attempt: LoginAttempt
        ): Boolean = error("Unexpected renewal")

        override suspend fun accept(
            expectedGeneration: Long,
            identity: SenderIdentity,
            attempt: LoginAttempt
        ): Boolean = error("Unexpected login")
    }
    val renewal = CredentialRenewal(store, { _, _ -> error("Unexpected renewal") }, scope)
    val inventory = AccountSenders(
        store,
        renewal,
        {
            listOf(
                AccountSender("example-sender", "小米 15"),
                AccountSender("pixel", "Pixel 9"),
                AccountSender("samsung", "Galaxy S25")
            )
        },
        scope
    )
    val revocation = SenderRevocation(
        store,
        inventory,
        renewal,
        authorization,
        service,
        scope
    )
    val app = activity.application as PinGlideApplication
    InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
    runOnUiThread { activity.enableEdgeToEdge() }
    setContent {
        LocalizedContent(language) {
            PinGlideTheme {
                SettingsNavigation(
                    language = language,
                    onLanguage = {},
                    onBatterySettings = { false },
                    installedVersion = "0.1.0",
                    account = app.accountSession,
                    senders = inventory,
                    revocation = revocation,
                    onLogout = { error("Unexpected logout") },
                    onDismiss = {}
                )
            }
        }
    }
    onNodeWithText(
        if (language == AppLanguage.English) "Device access management" else "设备访问管理"
    ).performScrollTo().performClick()
    onNodeWithText("Pixel 9").performScrollTo().assertIsDisplayed()
}
