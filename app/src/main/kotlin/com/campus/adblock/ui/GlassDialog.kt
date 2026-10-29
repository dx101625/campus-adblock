/*
 * Copyright 2025 Kyant
 * SPDX-License-Identifier: Apache-2.0
 * Glass surface adapted from Backdrop's DialogContent example.
 */
package com.campus.adblock.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private class GlassDialogEntry(val owner: Any, val title: () -> String,
    val summary: () -> String?, val dismiss: () -> Unit, val content: @Composable () -> Unit)
private class GlassDialogs {
    val entries = mutableStateListOf<GlassDialogEntry>()
    fun remove(owner: Any) { entries.removeAll { it.owner === owner } }
}
private val LocalGlassDialogs = staticCompositionLocalOf<GlassDialogs> { error("Glass dialog host missing") }

/** Render in the same root as the page recorder: live blur, no screenshot or extra window. */
@Composable internal fun GlassDialogHost(backdrop: Backdrop, animations: Boolean, highContrast: Boolean, content: @Composable () -> Unit) {
    val dialogs = remember { GlassDialogs() }
    CompositionLocalProvider(LocalGlassDialogs provides dialogs, LocalMenuMotion provides animations) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(if (dialogs.entries.isNotEmpty()) Modifier.clearAndSetSemantics {} else Modifier)) { content() }
            dialogs.entries.forEachIndexed { index, entry ->
                key(entry.owner) {
                    GlassDialogSurface(entry, backdrop, animations, highContrast, index == dialogs.entries.lastIndex)
                }
            }
        }
    }
}

/** Registration is tied to the caller's lifetime; nested editors and choices form a stack. */
@Composable internal fun GlassDialog(show: Boolean, title: String, summary: String? = null,
    onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    val dialogs = LocalGlassDialogs.current
    val owner = remember { Any() }
    val currentTitle by rememberUpdatedState(title)
    val currentSummary by rememberUpdatedState(summary)
    val dismiss by rememberUpdatedState(onDismissRequest)
    val body by rememberUpdatedState(content)
    DisposableEffect(show, dialogs) {
        if (show) dialogs.entries.add(GlassDialogEntry(owner, { currentTitle }, { currentSummary }, { dismiss() }, { body() }))
        onDispose { dialogs.remove(owner) }
    }
}

@Composable private fun GlassDialogSurface(entry: GlassDialogEntry, backdrop: Backdrop,
    animations: Boolean, highContrast: Boolean, top: Boolean) {
    val dark = MiuixTheme.colorScheme.surface.luminance() < .5f
    val shape = RoundedCornerShape(28.dp)
    val progress = remember { Animatable(if (animations) 0f else 1f) }
    LaunchedEffect(animations) { progress.animateTo(1f, tween(if (animations) 160 else 0)) }
    BackHandler(enabled = top, onBack = entry.dismiss)
    BoxWithConstraints(Modifier.fillMaxSize().then(if (!top) Modifier.clearAndSetSemantics {} else Modifier), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (if (dark) .25f else .12f) * progress.value))
            .clickable(interactionSource = null, indication = null, onClickLabel = "关闭", onClick = entry.dismiss))
        val optical = Build.VERSION.SDK_INT >= 31 && !highContrast
        val surface = if (optical) Modifier.drawBackdrop(
            backdrop = backdrop, shape = { shape },
            effects = { vibrancy(); blur(18.dp.toPx()); lens(8.dp.toPx(), 16.dp.toPx()) },
            highlight = { Highlight(width = .8.dp, alpha = if (dark) .25f else .80f) },
            shadow = { Shadow(radius = 20.dp, color = Color.Black.copy(alpha = if (dark) .25f else .13f)) },
            onDrawSurface = { drawRect((if (dark) Color(0xFF202126) else Color(0xFFFAFBFF)).copy(alpha = if (dark) .78f else .68f)) },
        ) else Modifier.background(MiuixTheme.colorScheme.surfaceContainer, shape)
            .border(1.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = .12f), shape)
        Column(Modifier.padding(20.dp).widthIn(max = 360.dp).fillMaxWidth()
            .heightIn(max = (maxHeight - 40.dp).coerceAtLeast(100.dp)).testTag("glass_dialog")
            .menuEnterMotion(panel = true)
            .then(surface).pointerInput(Unit) { detectTapGestures {} }
            .semantics { paneTitle = entry.title() }.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(entry.title(), modifier = Modifier.weight(1f), color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Box(Modifier.padding(start = 8.dp).size(36.dp).clip(RoundedCornerShape(18.dp))
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = .06f)).testTag("glass_dialog_close")
                    .clickable(role = Role.Button, onClickLabel = "关闭", onClick = entry.dismiss), contentAlignment = Alignment.Center) {
                    Text("×", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 24.sp)
                }
            }
            entry.summary()?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp))
            }
            Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())) { entry.content() }
        }
    }
}
