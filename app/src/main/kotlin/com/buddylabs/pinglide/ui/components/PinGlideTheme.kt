package com.buddylabs.pinglide.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import com.buddylabs.pinglide.R

@Composable
fun PinGlideTheme(content: @Composable () -> Unit) {
    val base = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(
        colorScheme = base.copy(
            primary = colorResource(R.color.primary),
            onPrimary = colorResource(R.color.on_primary),
            primaryContainer = colorResource(R.color.primary_container),
            onPrimaryContainer = colorResource(R.color.on_primary_container),
            secondary = colorResource(R.color.secondary),
            secondaryContainer = colorResource(R.color.secondary_container),
            onSecondaryContainer = colorResource(R.color.on_secondary_container),
            background = colorResource(R.color.surface),
            onBackground = colorResource(R.color.on_surface),
            surface = colorResource(R.color.surface),
            onSurface = colorResource(R.color.on_surface),
            onSurfaceVariant = colorResource(R.color.on_surface_variant),
            surfaceContainer = colorResource(R.color.surface_container),
            surfaceContainerLowest = colorResource(R.color.surface),
            surfaceContainerLow = colorResource(R.color.surface_container),
            surfaceContainerHigh = colorResource(R.color.surface_container_high),
            surfaceContainerHighest = colorResource(R.color.surface_container_high),
            surfaceTint = colorResource(R.color.primary),
            outline = colorResource(R.color.outline),
            outlineVariant = colorResource(R.color.outline_variant)
        ),
        content = content
    )
}
