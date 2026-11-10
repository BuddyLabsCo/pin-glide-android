package com.buddylabs.pinglide.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    SettingsGroupTitle(stringResource(R.string.settings_app_language))
    Card {
        Column(Modifier.selectableGroup()) {
            AppLanguage.entries.forEach { choice ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .selectable(
                            selected = language == choice,
                            role = Role.RadioButton,
                            onClick = { onLanguage(choice) }
                        ).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RadioButton(selected = language == choice, onClick = null)
                    Text(languageLabel(choice))
                }
            }
        }
    }
}
