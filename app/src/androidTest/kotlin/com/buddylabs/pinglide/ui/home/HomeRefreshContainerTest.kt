package com.buddylabs.pinglide.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.domain.identity.AuthorizationState
import com.buddylabs.pinglide.domain.pairing.HomeReadiness
import com.buddylabs.pinglide.domain.pairing.HomeState
import com.buddylabs.pinglide.ui.components.PinGlideTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeRefreshContainerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun pullAndEnterRequestRefreshOnlyBeforeSignInExpires() {
        val state = mutableStateOf(HomeState(authorization = AuthorizationState.Ready))
        var requests = 0
        compose.setContent {
            val listState = rememberLazyListState()
            val readiness = state.value.readiness(smsGranted = true)
            PinGlideTheme {
                HomeRefreshContainer(
                    state.value,
                    showListProgress = false,
                    listState = listState,
                    onRefresh = { requests++ },
                    modifier = Modifier.height(320.dp),
                    enabled = readiness != HomeReadiness.LoginRequired
                ) { refreshHint ->
                    LazyColumn(state = listState) {
                        item {
                            refreshHint()
                            Text("Device list", Modifier.fillMaxWidth().height(80.dp).focusable())
                        }
                    }
                }
            }
        }

        val list = compose.onNode(hasScrollAction())
        val listItem = compose.onNodeWithText("Device list")
        val pull = {
            list.performTouchInput {
                down(center.copy(y = 48.dp.toPx()))
                moveBy(Offset(0f, 216.dp.toPx()))
                up()
            }
        }
        val pressEnter = {
            listItem.performSemanticsAction(SemanticsActions.RequestFocus)
            listItem.performKeyInput { pressKey(Key.Enter) }
        }

        pull()
        compose.runOnIdle { assertEquals("Pull before expiry", 1, requests) }
        pressEnter()
        compose.runOnIdle { assertEquals("Enter before expiry", 2, requests) }

        val expired = HomeState(authorization = AuthorizationState.LoginRequired)
        compose.runOnIdle { state.value = expired }
        pull()
        compose.runOnIdle { assertEquals("Pull after expiry", 2, requests) }
        pressEnter()
        compose.runOnIdle { assertEquals("Enter after expiry", 2, requests) }
    }
}
