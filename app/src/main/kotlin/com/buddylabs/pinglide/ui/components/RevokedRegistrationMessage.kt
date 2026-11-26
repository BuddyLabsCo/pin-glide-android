package com.buddylabs.pinglide.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R

@Composable
internal fun RevokedRegistrationMessage(
    title: String,
    description: String,
    headingModifier: Modifier
) {
    Surface(
        modifier = Modifier.padding(bottom = 18.dp),
        shape = RoundedCornerShape(24.dp),
        color = colorResource(R.color.error_emblem_container)
    ) {
        Icon(
            painterResource(R.drawable.ic_registration_revoked_lock),
            contentDescription = null,
            tint = colorResource(R.color.error_emblem),
            modifier = Modifier.padding(21.dp).size(30.dp)
        )
    }
    Text(
        title,
        headingModifier,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.04).sp
        ),
        textAlign = TextAlign.Center
    )
    Text(
        description,
        Modifier.padding(top = 10.dp).widthIn(max = 300.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = 13.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp
        ),
        textAlign = TextAlign.Center
    )
}
