/*
 * Copyright 2025 Kyant
 * Copyright 2026 compose-miuix-ui contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Layered lens adapted from the Apache-2.0 LiquidBottomTabs and
 * Miuix IosLiquidGlassNavigationBar examples. Interaction and compact
 * three-tab layout are integrated with Campus AdBlock's existing UI.
 */
package com.campus.adblock.ui

import android.os.Build
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campus.adblock.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The upper lens refracts a separate blue icon/text layer as well as the page. */
@Composable
fun GlassNavigation(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: Backdrop,
    animations: Boolean,
    highContrast: Boolean,
    modifier: Modifier = Modifier,
) {
    val night = MiuixTheme.colorScheme.surface.luminance() < .5f
    val capsule = RoundedCornerShape(100.dp)
    val optical = Build.VERSION.SDK_INT >= 31 && !highContrast
    val container = (if (night) Color(0xFF121212) else Color(0xFFFAFAFA)).copy(alpha = .40f)
    val accent = MiuixTheme.colorScheme.primary
    val foreground = MiuixTheme.colorScheme.onSurface.copy(alpha = .92f)
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
    var position by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    var edgePull by remember { mutableFloatStateOf(0f) }
    val recoil = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var recoilJob by remember { mutableStateOf<Job?>(null) }
    val activate by rememberUpdatedState(onSelected)
    val activeIndex by rememberUpdatedState(selectedIndex)
    val motionEnabled by rememberUpdatedState(animations)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val press by animateFloatAsState(
        if (pressed || dragging) 1f else 0f,
        animationSpec = tween(if (animations) 160 else 0), label = "glass-lens-press",
    )

    LaunchedEffect(selectedIndex, dragging, animations) {
        if (!dragging) {
            if (animations) animate(position, selectedIndex.toFloat(), animationSpec = spring(dampingRatio = .82f, stiffness = 650f)) { value, _ -> position = value }
            else position = selectedIndex.toFloat()
        }
    }

    LaunchedEffect(animations) { if (!animations) { recoilJob?.cancel(); recoil.snapTo(0f); edgePull = 0f; dragVelocity = 0f } }
    val bend by animateFloatAsState(if (animations && dragging) dragVelocity else 0f, tween(if (animations) 90 else 0), label = "glass-drag-bend")
    BoxWithConstraints(modifier.testTag("glass_navigation").graphicsLayer {
        translationX = if (animations) recoil.value.dp.toPx() else 0f
        rotationZ = if (animations) recoil.value * .12f else 0f
    }, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val slot = constraints.maxWidth / 3f
        val inset = with(density) { 4.dp.toPx() }
        val gesture = Modifier.fillMaxSize()
            .pointerInput(Unit) {
                // Observe the press without consuming it: children retain tap,
                // keyboard and accessibility activation; drag owns only motion.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pressed = true
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                    } finally { pressed = false }
                }
            }
            .pointerInput(slot, rtl) {
                val tracker = VelocityTracker()
                var rawPosition = position
                fun publish() {
                    // A small dead band around tab boundaries avoids chatter.
                    val candidate = position.roundToInt().coerceIn(0, 2)
                    if (candidate != activeIndex && abs(position - activeIndex) > .57f) activate(candidate)
                }
                fun settle(cancelled: Boolean) {
                    val velocity = if (cancelled) 0f else tracker.calculateVelocity().x / density.density
                    val kick = if (abs(velocity) < 420f) 0f else (velocity / 250f).coerceIn(-5.5f, 5.5f)
                    dragging = false; edgePull = 0f; dragVelocity = 0f
                    recoilJob?.cancel()
                    recoilJob = scope.launch {
                        recoil.snapTo(if (motionEnabled) kick else 0f)
                        if (motionEnabled) recoil.animateTo(0f, spring(dampingRatio = .44f, stiffness = 420f))
                    }
                }
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        recoilJob?.cancel(); recoilJob = scope.launch { recoil.snapTo(0f) }
                        tracker.resetTracking(); dragVelocity = 0f
                        dragging = true
                        val raw = (offset.x / slot - .5f).coerceIn(0f, 2f)
                        position = if (rtl) 2f - raw else raw
                        rawPosition = position; publish()
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        dragVelocity = (tracker.calculateVelocity().x / density.density / 1400f).coerceIn(-1f, 1f)
                        rawPosition += amount / slot * if (rtl) -1f else 1f
                        position = rawPosition.coerceIn(0f, 2f)
                        edgePull = if (motionEnabled) ((rawPosition - position) * .15f).coerceIn(-.12f, .12f) else 0f
                        publish()
                    },
                    onDragEnd = { settle(false) },
                    onDragCancel = { settle(true) },
                )
            }

        val base = if (optical) Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { capsule },
            effects = { vibrancy(); blur(4.dp.toPx()); lens(24.dp.toPx(), 24.dp.toPx()) },
            highlight = { Highlight(width = .8.dp, alpha = .65f) },
            shadow = { Shadow(radius = 10.dp, color = Color.Black.copy(alpha = if (night) .20f else .10f)) },
            onDrawSurface = { drawRect(container) },
        ) else Modifier
            .background(if (night) Color(0xF2242424) else Color(0xF2FFFFFF), capsule)
            .border(1.dp, Color.White.copy(alpha = if (night) .25f else .75f), capsule)
        Box(Modifier.fillMaxSize().then(base))

        // Visible row owns all semantics. Optical selection is drawn on top.
        TabRow(selectedIndex, activate, accent, foreground,
            allTint = if (optical) foreground else null,
            modifier = gesture, interactive = true)

        if (optical) {
            // Alpha is outside the recorder: this row stays invisible but its
            // opaque page/glass surface and blue glyphs remain available to the
            // selector. It must never export duplicate accessibility nodes.
            TabRow(selectedIndex, activate, accent, foreground,
                allTint = accent, itemScale = 1f + .20f * press, interactive = false,
                modifier = Modifier.fillMaxSize().clearAndSetSemantics {}.alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .drawBackdrop(
                        backdrop = backdrop, shape = { capsule },
                        effects = { vibrancy(); blur(4.dp.toPx()); lens(24.dp.toPx(), 24.dp.toPx()) },
                        highlight = null, shadow = null,
                        onDrawSurface = { drawRect(container) },
                    ),
            )
        }

        val selector = if (optical) Modifier.drawBackdrop(
            backdrop = combinedBackdrop,
            shape = { capsule },
            effects = {
                lens((2f + 8f * press).dp.toPx(), (2f + 12f * press).dp.toPx(),
                    depthEffect = true, chromaticAberration = true)
            },
            highlight = { Highlight(width = 1.dp, blurRadius = .5.dp,
                alpha = .3f + .7f * press, style = HighlightStyle.Default(angle = -45f)) },
            shadow = { Shadow(radius = 8.dp, alpha = .6f * press) },
            innerShadow = { InnerShadow(radius = 7.dp, alpha = press) },
            layerBlock = { scaleX = 1f + .30f * press + .06f * abs(bend); scaleY = 1f + .45f * press - .03f * abs(bend); rotationZ = bend * 2f },
            onDrawSurface = {
                drawRect(if (night) Color.White.copy(alpha = .10f) else Color.Black.copy(alpha = .07f), alpha = 1f - press)
                drawRect(Color.Black.copy(alpha = .025f * press))
            },
        ) else Modifier.background(accent.copy(alpha = if (night) .20f else .12f), capsule)
        Box(Modifier.offset {
            IntOffset(((if (rtl) 2f - position - edgePull else position + edgePull) * slot + inset).roundToInt(), 0)
        }.width(with(density) { (slot - 2 * inset).toDp() }).height(48.dp)
            .clearAndSetSemantics {}.then(selector))
    }
}

@Composable
private fun TabRow(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    accent: Color,
    foreground: Color,
    modifier: Modifier,
    allTint: Color? = null,
    itemScale: Float = 1f,
    interactive: Boolean,
) {
    val labels = listOf(stringResource(R.string.tab_home), stringResource(R.string.tab_features), stringResource(R.string.tab_about))
    val icons = listOf(R.drawable.ic_tab_home, R.drawable.ic_tab_features, R.drawable.ic_tab_about)
    Row(modifier.then(if (interactive) Modifier.selectableGroup() else Modifier), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, label ->
            val active = selectedIndex == index
            val tint = allTint ?: if (active) accent else foreground
            Column(Modifier.weight(1f).fillMaxHeight().then(
                if (interactive) Modifier.testTag("tab_$index")
                    .selectable(selected = active, onClick = { onSelected(index) }, role = Role.Tab)
                else Modifier
            ).graphicsLayer { scaleX = itemScale; scaleY = itemScale },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(imageVector = ImageVector.vectorResource(icons[index]), contentDescription = null,
                    colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(22.dp))
                Text(label, color = tint, fontSize = 11.sp, lineHeight = 13.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}
