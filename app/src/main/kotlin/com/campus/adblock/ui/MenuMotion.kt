package com.campus.adblock.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

internal val LocalMenuMotion = compositionLocalOf { true }

/** A restrained shared entrance for every secondary page and floating panel. */
@Composable internal fun Modifier.menuEnterMotion(panel: Boolean = false): Modifier {
    val enabled = LocalMenuMotion.current
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(enabled) {
        if (enabled) progress.animateTo(1f, spring(dampingRatio = .64f, stiffness = 420f))
        else progress.snapTo(1f)
    }
    return graphicsLayer {
        val value = progress.value
        alpha = value.coerceIn(0f, 1f)
        translationY = (if (panel) 18.dp else 12.dp).toPx() * (1f - value)
        val amount = if (panel) .06f else .012f
        scaleX = 1f - amount * (1f - value)
        scaleY = scaleX
    }
}
