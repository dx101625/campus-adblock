package com.campus.adblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.campus.adblock.R
import com.campus.adblock.SettingsActivity
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun FrameworkActivationCard(activity: SettingsActivity, state: FrameworkActivation.Snapshot,
    mode: String, modeSummary: String, onMode: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val active = state.status == FrameworkActivation.Status.ACTIVE
    val checking = state.status == FrameworkActivation.Status.CHECKING
    val dark = MiuixTheme.colorScheme.surface.luminance() < .3f
    val accent = when {
        active -> if (dark) Color(0xFF7FE1AE) else Color(0xFF167A49)
        checking -> MiuixTheme.colorScheme.primary
        else -> if (dark) Color(0xFFF2CD81) else Color(0xFF8D5B10)
    }
    val info = state.frameworks.firstOrNull()
    val scope = state.frameworks.flatMap { it.scope }.toSet()
    val targetCount = listOf(StatusController.Q, StatusController.Z).count { it in scope }
    val title = when {
        active -> "模块已激活"
        checking -> "正在检测激活状态"
        state.status == FrameworkActivation.Status.UNSUPPORTED -> "当前系统不支持框架状态服务"
        else -> "未连接到 LSPosed 框架"
    }
    val summary = when {
        active -> "${info?.name} · API ${info?.api} · 已选 $targetCount/2 个目标应用"
        checking -> "正在等待框架服务连接"
        state.status == FrameworkActivation.Status.UNSUPPORTED -> "可在 Android 8.0 及以上查询框架连接状态"
        else -> "请在框架中启用模块，并勾选目标应用"
    }
    Card(Modifier.fillMaxWidth().testTag("home_summary"), cornerRadius = 26.dp) {
        Row(Modifier.fillMaxWidth().testTag("home_framework_" + state.status.name.lowercase())
            .clickable(role = Role.Button, onClickLabel = "查看模块激活状态") { expanded = true }
            .background(accent.copy(alpha = if (dark) .09f else .055f)).padding(horizontal = 17.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
                AppGlyph(if (active) R.drawable.ic_success else if (checking) R.drawable.ic_clock else R.drawable.ic_info_status, tint = accent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = accent, style = MiuixTheme.textStyles.headline1)
                Text(summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
            }
            AppGlyph(R.drawable.ic_chevron_right, size = 17.dp, tint = accent.copy(alpha = .65f))
        }
        Box(Modifier.padding(horizontal = 17.dp).fillMaxWidth().height(.5.dp).background(MiuixTheme.colorScheme.dividerLine))
        Row(Modifier.fillMaxWidth().testTag("home_mode").clickable(role = Role.Button, onClickLabel = "调整使用习惯", onClick = onMode)
            .padding(horizontal = 17.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MiuixTheme.colorScheme.primary.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
                AppGlyph(R.drawable.ic_tab_features, tint = MiuixTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("普通打开胖乖 · $mode", color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.headline1)
                Text(modeSummary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
            }
            AppGlyph(R.drawable.ic_chevron_right, size = 17.dp, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = .65f))
        }
    }
    GlassDialog(show = expanded, title = "模块与框架状态", onDismissRequest = { expanded = false }) {
        val height = minOf(340.dp, LocalConfiguration.current.screenHeightDp.dp * .45f)
        Column(Modifier.heightIn(max = height).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = accent, style = MiuixTheme.textStyles.headline1)
            if (active) {
                state.frameworks.forEach { framework ->
                    Text("${framework.name} ${framework.version}\n服务 API ${framework.api}", color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.body2)
                }
                Text("目标应用作用域\n胖乖生活：${if (StatusController.Q in scope) "已勾选" else "未勾选"}\n智趣校园：${if (StatusController.Z in scope) "已勾选" else "未勾选"}", color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.body2)
                Text("已收到框架的实时服务连接。目标应用里的具体执行结果，可查看首页运行记录与兼容性体检。", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
            } else Text("在 LSPosed 或兼容框架中启用本模块，勾选胖乖生活或智趣校园，再重开模块设置页。旧框架可能没有提供状态服务；下方的历史加载时间不能确认本次激活。", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolAction("打开框架管理", Modifier.weight(1f)) { expanded = false;activity.openLsposed() }
            ToolAction("重新检测", Modifier.weight(1f), true) { FrameworkActivation.getInstance().refresh() }
        }
        ToolAction("关闭", Modifier.fillMaxWidth()) { expanded = false }
    }
}
