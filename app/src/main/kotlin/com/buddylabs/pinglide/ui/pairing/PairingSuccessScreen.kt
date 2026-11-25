package com.buddylabs.pinglide.ui.pairing

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.ui.components.PairingTestAction
import com.buddylabs.pinglide.ui.components.RingedEmblem
import com.buddylabs.pinglide.ui.components.TestMessageAction

@Composable
internal fun PairingSuccessScreen(action: TestMessageAction, onHome: () -> Unit) {
    val title = stringResource(R.string.pairing_success)
    PairingTestAction(action) { sendButton, notice ->
        PairingResultLayout(
            title,
            bodyBottomPadding = 38.dp,
            body = {
                RingedEmblem(
                    RoundedCornerShape(44.dp),
                    containerColor = colorResource(R.color.pairing_success_container)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        null,
                        Modifier.size(54.dp).testTag("pairing-success-check"),
                        tint = colorResource(R.color.home_online)
                    )
                }
                PairingAnnouncedHeading(
                    title,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 32.sp,
                        lineHeight = 35.sp,
                        fontWeight = FontWeight(760),
                        letterSpacing = (-1.76).sp,
                        textAlign = TextAlign.Center
                    )
                )
            },
            actions = {
                sendButton()
                TextButton(
                    onClick = onHome,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.pairing_home), fontWeight = FontWeight.Bold)
                }
            },
            overlay = notice
        )
    }
}
