package com.buddylabs.pinglide.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.buddylabs.pinglide.R
import com.buddylabs.pinglide.domain.settings.AppLanguage

@Composable
internal fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.System -> R.string.settings_follow_system
        AppLanguage.SimplifiedChinese -> R.string.settings_simplified_chinese
        AppLanguage.English -> R.string.settings_english
    }
)

@Composable
internal fun LanguageScreen(language: AppLanguage, onLanguage: (AppLanguage) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    SettingsGroup(
        stringResource(R.string.settings_app_language),
        modifier = Modifier.selectableGroup()
    ) {
        AppLanguage.entries.forEach { choice ->
            val selected = language == choice
            val last = choice == AppLanguage.entries.last()
            SettingsListRow(
                title = languageLabel(choice),
                modifier = Modifier.selectable(
                    selected = selected,
                    role = Role.RadioButton,
                    onClick = { onLanguage(choice) }
                ),
                minHeight = if (last) 60.dp else 59.dp,
                leading = {
                    Box(
                        Modifier.size(20.dp).border(
                            width = if (selected) 6.dp else 2.dp,
                            color = if (selected) {
                                colors.primary
                            } else {
                                colors.onSurface.copy(alpha = if (dark) 0.32f else 0.34f)
                            },
                            shape = CircleShape
                        )
                    )
                }
            )
            if (!last) {
                HorizontalDivider(color = colors.onSurface.copy(alpha = if (dark) 0.16f else 0.17f))
            }
        }
    }
}
