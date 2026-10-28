package com.campus.adblock.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Icons are read locally from installed packages; no third-party artwork is bundled. */
internal data class InstalledTarget(val version: String?, val icon: ImageBitmap?)

@Composable internal fun rememberTarget(context: Context, pkg: String, size: Dp = 44.dp): InstalledTarget {
    val pixels = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(1) }
    val revision = LocalPreferencesRevision.current
    return remember(context, pkg, pixels, revision) {
        val pm = context.packageManager
        val version = runCatching { pm.getPackageInfo(pkg, 0).versionName }.getOrNull()
        val bitmap = runCatching {
            val drawable = pm.getApplicationIcon(pkg)
            Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888).also {
                drawable.setBounds(0, 0, pixels, pixels); drawable.draw(Canvas(it))
            }.asImageBitmap()
        }.getOrNull()
        InstalledTarget(version, bitmap)
    }
}

@Composable internal fun TargetIcon(target: InstalledTarget, fallback: String, size: Dp = 44.dp) {
    if (target.icon != null) Image(target.icon, null, Modifier.size(size).clip(RoundedCornerShape(size * .25f)))
    else Box(Modifier.size(size).clip(RoundedCornerShape(size * .28f)).background(MiuixTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
        Text(fallback, style = MiuixTheme.textStyles.headline1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable internal fun AppGlyph(@DrawableRes resource: Int, modifier: Modifier = Modifier, size: Dp = 22.dp,
    tint: Color = MiuixTheme.colorScheme.onSurface, description: String? = null) {
    Image(painterResource(resource), description, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}
