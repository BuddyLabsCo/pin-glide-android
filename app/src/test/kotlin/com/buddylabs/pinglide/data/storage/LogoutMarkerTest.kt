package com.buddylabs.pinglide.data.storage

import com.buddylabs.pinglide.domain.identity.IdentitySnapshot
import com.buddylabs.pinglide.domain.identity.RefreshState
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogoutMarkerTest {
    @get:Rule
    val directory = TemporaryFolder()

    @Test
    fun retainedLogoutMarkerCannotMaskAnAlreadyCommittedLogin() {
        val marker = directory.newFile("identity.logged-out")
        val loggedOut = IdentitySnapshot(generation = 2, refreshState = RefreshState.LoggedOut)
        marker.markLogout(loggedOut)

        assertTrue(marker.appliesTo(IdentitySnapshot(generation = 1)))
        assertTrue(marker.appliesTo(loggedOut))
        assertFalse(marker.appliesTo(IdentitySnapshot(generation = 3)))
    }

    @Test
    fun legacyMarkerProtectsOldCredentialsUntilBoundBeforeLogin() {
        val marker = directory.newFile("identity.logged-out")
        marker.writeBytes(byteArrayOf(1))
        assertTrue(marker.appliesTo(IdentitySnapshot(generation = 3)))

        marker.markLogout(IdentitySnapshot(generation = 2))

        assertTrue(marker.appliesTo(IdentitySnapshot(generation = 2)))
        assertFalse(marker.appliesTo(IdentitySnapshot(generation = 3)))
    }

    @Test
    fun missingMarkerDoesNotDisableAuthority() {
        assertFalse(File(directory.root, "missing").appliesTo(IdentitySnapshot(generation = 1)))
    }

    @Test
    fun corruptedMarkerCannotSilentlyEnableOldCredentials() {
        val marker = directory.newFile("identity.logged-out")
        marker.writeText("invalid")

        assertThrows(NumberFormatException::class.java) {
            marker.appliesTo(IdentitySnapshot(generation = 1))
        }
    }
}
