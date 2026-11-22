package com.buddylabs.pinglide.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddylabs.pinglide.R

@Composable
internal fun WarningInfoCard(
    @StringRes message: Int,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int = R.drawable.ic_info
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorResource(R.color.setup_warning_container),
        contentColor = colorResource(R.color.setup_on_warning),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.padding(top = 1.dp).size(18.dp)
            )
            Text(
                stringResource(message),
                style = MaterialTheme.typography.bodySmall.copy(
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp
                )
            )
        }
    }
}
