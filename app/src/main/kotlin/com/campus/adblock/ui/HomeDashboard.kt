package com.campus.adblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campus.adblock.*
import com.campus.adblock.R
import org.json.JSONArray
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun HomeHeader(activity: SettingsActivity, version: String, release: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        TargetIcon(rememberTarget(activity, activity.packageName, 40.dp), "校", 40.dp)
        Column(Modifier.weight(1f).padding(start = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("校园设备去广告", color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.title3.copy(fontSize = 19.sp), fontWeight = FontWeight.Medium)
            Text("少一点广告，少几步操作", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
        }
        Text(version, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote2,
            modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(20.dp)).background(MiuixTheme.colorScheme.surfaceContainer)
                .clickable(role = Role.Button, onClickLabel = "更新记录", onClick = release).padding(horizontal = 10.dp, vertical = 7.dp))
    }
}

/** The dashboard reflects preferences and historical evidence, never an inferred live hook status. */
@Composable internal fun HomeDashboard(activity: SettingsActivity, qBeat: StatusController.Beat, zBeat: StatusController.Beat, activation: FrameworkActivation.Snapshot,
    onMode: () -> Unit, onApp: (String) -> Unit, onDevices: () -> Unit, onStatus: () -> Unit, onHealth: () -> Unit) {
    val revision = LocalPreferencesRevision.current
    val ui = remember(activity) { UiKit(activity) }
    val devices = remember(revision) { LocalData.devices(activity) }
    val preferences = activity.getSharedPreferences(Cfg.GROUP, 0)
    val selected = devices.firstOrNull { it.id == Cfg.uiString(activity, "q.profile", "") && it.qr == Cfg.uiString(activity, "q.qr", "") }
        ?: if (!preferences.contains("q.profile")) devices.firstOrNull { it.qr == Cfg.uiString(activity, "q.qr", "") } else null
    val global = AutomationStore.mode(preferences)
    val profileMode = selected?.options?.mode?.takeIf { it != "inherit" } ?: global
    val ordinary = PreferenceRules.launchMode(global, profileMode, Cfg.uiString(activity, "q.launch", "direct") == "shortcut", false)
    val recent = remember(revision, qBeat.time, zBeat.time) {
        val events = runCatching { JSONArray(activity.getSharedPreferences(RuntimeEvents.GROUP, 0).getString("events", "[]")) }.getOrDefault(JSONArray())
        (events.length() - 1 downTo 0).asSequence().mapNotNull { events.optJSONObject(it) }.firstOrNull { it.optString("kind") != "loaded" }
    }
    val colors = MiuixTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FrameworkActivationCard(activity, activation,
            mode = when (ordinary) { "ads" -> "仅去广告"; "device" -> "停在设备页"; else -> "订单预览" },
            modeSummary = when (ordinary) { "ads" -> "扫码、选机与支付由你操作"; "device" -> "识别并预选，停在设备页"; else -> "有设备码时识别预选，付款由你确认" }, onMode = onMode)
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            DashboardLabel("我的应用", "点击调整")
            // Flow into a column on a narrow window or at a large font scale.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val stack = maxWidth < 330.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.2f
                if (stack) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppStatusCard(activity, StatusController.Q, "胖乖生活", "胖", "1.144.0", qBeat, ui, Modifier.fillMaxWidth()) { onApp("qiekj") }
                    AppStatusCard(activity, StatusController.Z, "智趣校园", "趣", "6.5.29", zBeat, ui, Modifier.fillMaxWidth()) { onApp("zqxy") }
                } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppStatusCard(activity, StatusController.Q, "胖乖生活", "胖", "1.144.0", qBeat, ui, Modifier.weight(1f)) { onApp("qiekj") }
                    AppStatusCard(activity, StatusController.Z, "智趣校园", "趣", "6.5.29", zBeat, ui, Modifier.weight(1f)) { onApp("zqxy") }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            DashboardLabel("常用设备", if (devices.isEmpty()) "" else "${devices.size}台收藏")
            Card(Modifier.fillMaxWidth()) {
                DashboardRow(if (selected != null) selected.name else "添加常用设备",
                    selected?.let { listOfNotNull(it.group.takeIf(String::isNotBlank), "设备入口：${LocalData.modeLabel(profileMode)}").joinToString(" · ") }
                        ?: "导入设备码，或在胖乖里主动扫码后收藏",
                    when (selected?.icon) { "hair" -> R.drawable.ic_device_hair; "water" -> R.drawable.ic_device_water; "washer" -> R.drawable.ic_device_washer; else -> R.drawable.ic_scan },
                    "home_devices", onDevices)
                if (selected != null) Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolAction("按档案打开", Modifier.weight(1f), true) { ProfileShortcuts.launch(activity, selected.id) }
                    ToolAction("管理设备", Modifier.weight(1f), action = onDevices)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            DashboardLabel("运行记录", "")
            Card(Modifier.fillMaxWidth()) {
                DashboardRow(if (recent == null) "还没有运行记录" else "最近一次执行",
                    if (recent == null) "重开目标应用后，可在这里查看处理结果。" else RuntimeEvents.clean(recent.optString("detail")),
                    R.drawable.ic_clock, "home_recent", onStatus,
                    recent?.let { StatusController.ago(ui, it.optLong("time")) })
                Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(.5.dp).background(colors.dividerLine))
                Row(Modifier.fillMaxWidth().testTag("home_health").clickable(role = Role.Button, onClickLabel = "兼容性体检", onClick = onHealth)
                    .padding(horizontal = 17.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppGlyph(R.drawable.ic_activity, size = 17.dp, tint = colors.onSurfaceVariantSummary)
                    Text("兼容性体检", color = colors.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1, modifier = Modifier.weight(1f).padding(start = 9.dp))
                    AppGlyph(R.drawable.ic_chevron_right, size = 16.dp, tint = colors.onSurfaceVariantSummary)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            AppGlyph(R.drawable.ic_shield, size = 13.dp, tint = colors.onSurfaceVariantSummary.copy(alpha = .7f))
            Text("配置仅保存在本机", color = colors.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote2, modifier = Modifier.padding(start = 5.dp))
        }
    }
}

@Composable private fun DashboardLabel(title: String, hint: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(hint, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote2)
    }
}

@Composable private fun AppStatusCard(activity: SettingsActivity, pkg: String, name: String, fallback: String, verified: String,
    beat: StatusController.Beat, ui: UiKit, modifier: Modifier, onClick: () -> Unit) {
    val target = rememberTarget(activity, pkg)
    Card(modifier.testTag("home_app_$pkg").clickable(role = Role.Button, onClickLabel = "$name 设置", onClick = onClick)) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TargetIcon(target, fallback)
                Spacer(Modifier.weight(1f))
                AppGlyph(R.drawable.ic_chevron_right, size = 17.dp, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = .6f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name, color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.headline1, fontWeight = FontWeight.Medium)
                Text(target.version?.let { "v$it" } ?: "未安装", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
            }
            val loaded = beat.time > 0
            val ink = if (loaded) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
            Row(Modifier.clip(RoundedCornerShape(9.dp)).background(ink.copy(alpha = .07f)).padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AppGlyph(if (loaded) R.drawable.ic_clock else R.drawable.ic_circle, size = 12.dp, tint = ink)
                Text(if (loaded) "${StatusController.ago(ui, beat.time)}加载" else "等待首次加载", color = ink, style = MiuixTheme.textStyles.footnote2,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (target.version != null && target.version != verified) Text("此版本尚未验证", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote2)
        }
    }
}

@Composable private fun DashboardRow(title: String, summary: String, icon: Int, tag: String, click: () -> Unit, hint: String? = null) {
    Row(Modifier.fillMaxWidth().testTag(tag).clickable(role = Role.Button, onClickLabel = title, onClick = click).padding(17.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(MiuixTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            AppGlyph(icon, size = 21.dp, tint = MiuixTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (hint != null) Text(hint, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote2)
            }
            Text(summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        AppGlyph(R.drawable.ic_chevron_right, size = 17.dp, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = .65f))
    }
}
