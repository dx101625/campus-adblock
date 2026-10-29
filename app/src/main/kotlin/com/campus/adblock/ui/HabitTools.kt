package com.campus.adblock.ui

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.campus.adblock.*
import com.campus.adblock.R
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** User-facing habits, temporary exceptions and installation diagnostics stay in the module process. */
class HabitTools(private val activity: SettingsActivity, private val changed: () -> Unit, private val details: () -> Unit, saved: android.os.Bundle? = null) {
    private var safety by mutableStateOf(saved?.getBoolean("safety") ?: false)
    private var health by mutableStateOf(saved?.getBoolean("health") ?: false)
    private var grants by mutableStateOf(saved?.getBoolean("grants") ?: false)
    private var accept by mutableStateOf(saved?.getBoolean("accept") ?: false)
    private var source by mutableStateOf(saved?.getString("source") ?: StatusController.Q)
    private var destination by mutableStateOf(saved?.getString("destination") ?: "com.sankuai.meituan")
    private var revision by mutableIntStateOf(0)
    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()
    private fun tick() { if (Cfg.uiBoolean(activity, "ui.haptics", true)) activity.window.decorView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    fun showSafety() { safety = true; revision++ }
    fun showHealth() { health = true; revision++ }
    fun showGrants() {
        val history = RuntimeStore.array(activity, "jumps")
        history.optJSONObject(history.length() - 1)?.let { source = it.optString("source", StatusController.Q); destination = it.optString("destination", "com.sankuai.meituan") }
        grants = true; revision++
    }

    @Composable private fun Choice(title: String, value: String, values: List<String>, labels: List<String>, tag: String, select: (String) -> Unit) {
        PreferenceChoice(title, labels, values.indexOf(value).coerceAtLeast(0), tag) { tick(); select(values[it]) }
    }
    @Composable fun Modes() {
        val mode = remember(LocalPreferencesRevision.current) { AutomationStore.mode(activity.getSharedPreferences(Cfg.GROUP, 0)) }
        Card(Modifier.fillMaxWidth()) {
            Choice("全局使用方式", mode, listOf("ads", "device", "preview"), listOf("仅去广告", "设备页", "订单预览"), "q.mode") {
                AutomationStore.setMode(activity, it); changed()
            }
            val explanation = when (mode) {
                "ads" -> "普通打开只清理广告，识别、选机、优惠和支付选择全部由你操作。主动使用独立设备入口时，遵循该档案的模式。"
                "device" -> "识别默认设备并按偏好预选，停在设备页；后续由你操作。"
                else -> "识别设备、应用预选并到达订单预览；最终付款由你确认。"
            }
            Text(explanation, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Choice("何时使用自动流程", Cfg.uiString(activity, "q.launch", "direct"), listOf("direct", "shortcut"), listOf("普通打开", "仅设备入口"), "q.launch") {
                Cfg.uiPutString(activity, "q.launch", it); changed()
            }
            Text("仅设备入口：平常打开胖乖只去广告；从桌面设备入口或档案按钮打开时，才应用该档案的使用方式。设备档案可覆盖全局偏好。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote2, modifier = Modifier.padding(16.dp))
        }
    }
    @Composable fun HomeDevice(manage: () -> Unit) {
        val devices = remember(LocalPreferencesRevision.current) { LocalData.devices(activity) }
        val preferences = activity.getSharedPreferences(Cfg.GROUP, 0)
        val selected = devices.firstOrNull { it.id == Cfg.uiString(activity, "q.profile", "") && it.qr == Cfg.uiString(activity, "q.qr", "") }
            ?: if (!preferences.contains("q.profile")) devices.firstOrNull { it.qr == Cfg.uiString(activity, "q.qr", "") } else null
        val global = AutomationStore.mode(activity.getSharedPreferences(Cfg.GROUP, 0))
        val mode = selected?.options?.mode?.takeIf { it != "inherit" } ?: global
        val ordinary = PreferenceRules.launchMode(global, mode, Cfg.uiString(activity, "q.launch", "direct") == "shortcut", false)
        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            BasicComponent(title = selected?.let { "默认设备 · ${it.name}" } ?: "默认设备", summary = listOfNotNull(selected?.group?.takeIf { it.isNotBlank() }, "普通打开：${LocalData.modeLabel(ordinary)}", selected?.let { "设备入口：${LocalData.modeLabel(mode)}" }).joinToString(" · "), startAction = { ProfileSymbol(selected?.icon ?: "other") })
            if (selected != null) Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolAction("按此档案打开胖乖", Modifier.weight(1f), true) { ProfileShortcuts.launch(activity, selected.id) }
                ToolAction("更换设备", Modifier.weight(1f), action = manage)
            } else BasicComponent(title = "管理常用设备", summary = if (Cfg.uiString(activity, "q.qr", "").isBlank()) "未选择档案时沿用原App记住的设备码；也可先收藏一台设备。" else "当前使用手填设备码；收藏并选为默认后，可使用独立档案。", onClick = manage)
        }
    }
    @Composable private fun Toggle(key: String, title: String, summary: String = "") {
        val value = remember(LocalPreferencesRevision.current, key) { Cfg.uiBoolean(activity, key, true) }
        SwitchPreference(checked = value, onCheckedChange = { Cfg.uiPutBoolean(activity, key, it); changed() }, title = title, summary = summary, modifier = Modifier.testTag(key))
    }
    @Composable fun Safeguards() {
        Toggle("q.pause", "连续失败后暂停直达", "同一设备连续识别失败3次后暂停；不会自动打开相机。")
        Toggle("q.price.guard", "报价与档位变化提醒", "同一设备、机号与偏好的报价或时长改变时，停在设备页。首次只建立本机参考，不代表已付款。")
        BasicComponent(title = "暂停状态与报价变化", summary = "恢复自动直达，或核对后接受新的报价参考", onClick = ::showSafety)
    }
    @Composable fun Appearance() {
        val revision = LocalPreferencesRevision.current
        val theme = remember(revision) { Cfg.uiString(activity, "ui.theme", "system") }
        val glass = remember(revision) { Cfg.uiString(activity, "ui.glass", "liquid") }
        Choice("界面主题", theme, listOf("system", "light", "dark"), listOf("跟随系统", "浅色", "深色"), "ui.theme") { Cfg.uiPutString(activity, "ui.theme", it); changed() }
        Choice("底栏材质 · 切换后直接预览", glass, listOf("liquid", "clear"), listOf("液态玻璃", "清晰轻量"), "ui.glass") { Cfg.uiPutString(activity, "ui.glass", it); changed() }
        Toggle("ui.motion", "界面动态效果", "关闭后减少页面与玻璃透镜动画，始终尊重系统关闭动画的设置。")
        Toggle("ui.haptics", "轻触反馈", "切换导航与使用模式时提供轻微触感，跟随系统触感开关。")
    }
    @Composable fun Dialogs() {
        revision
        val maxHeight = minOf(360.dp, LocalConfiguration.current.screenHeightDp.dp * .5f)
        GlassDialog(show = safety, title = "暂停状态与报价变化", onDismissRequest = { safety = false }) {
            Column {
                val failures = RuntimeStore.json(activity, "failures")
                val count = failures.keys().asSequence().count { failures.optInt(it) >= 3 }
                val quote = RuntimeStore.json(activity, "quote.pending")
                Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (count == 0) "当前没有因连续识别失败而暂停的设备。" else "$count 台设备因连续识别失败而暂停。恢复前请检查设备码与网络。", color = MiuixTheme.colorScheme.onSurface)
                    ToolAction("恢复自动直达", Modifier.fillMaxWidth(), true) { RuntimeStore.resume(activity); revision++; toast("已恢复，下一次打开目标App或设备入口时重试") }
                    if (quote.has("key")) {
                        Text("检测到报价或档位变化\n之前：${money(quote.optLong("oldCents"))}\n现在：${money(quote.optLong("cents"))}" +
                            if (kotlin.math.abs(quote.optDouble("oldDuration") - quote.optDouble("duration")) > .000001) "\n档位时长也发生了变化，请核对原App设备页。" else "",
                            color = MiuixTheme.colorScheme.onSurface)
                        ToolAction("核对并接受新参考", Modifier.fillMaxWidth()) { accept = true }
                    } else Text("暂无待确认的报价变化。参考来自原App展示的设备报价，保存在本机，不是支付或消费记录。", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
                }
                ToolAction("关闭", Modifier.fillMaxWidth()) { safety = false }
            }
        }
        GlassDialog(show = accept, title = "接受当前报价与档位？", summary = "仅更新本机参考，不会提交订单或付款。请先核对原App设备页。", onDismissRequest = { accept = false }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolAction("取消", Modifier.weight(1f)) { accept = false }
                ToolAction("接受新参考", Modifier.weight(1f), true) { RuntimeStore.acceptQuote(activity); accept = false; revision++; toast("参考已更新，下次使用设备入口时重新检查") }
            }
        }
        GlassDialog(show = grants, title = "临时放行跳转", summary = "只允许所选目标App打开所选应用；一次放行最迟5分钟失效，不会自动重放被拦截的跳转。", onDismissRequest = { grants = false }) {
            val grantHeight = minOf(520.dp, (LocalConfiguration.current.screenHeightDp.dp - 220.dp).coerceAtLeast(120.dp))
            Column(Modifier.heightIn(max = grantHeight).verticalScroll(rememberScrollState())) {
                Choice("发起跳转的App", source, listOf(StatusController.Q, StatusController.Z), listOf("胖乖生活", "智趣校园"), "grant.source") { source = it }
                Column(Modifier.heightIn(max = minOf(180.dp, grantHeight * .4f)).verticalScroll(rememberScrollState())) {
                    JumpPolicy.DESTINATIONS.forEach { (pkg, label) -> BasicComponent(title = label, endActions = { if (destination == pkg) AppGlyph(R.drawable.ic_check, size = 20.dp, tint = MiuixTheme.colorScheme.primary) }, onClick = { destination = pkg }) }
                }
                Text("当前：${JumpPolicy.DESTINATIONS[destination]}", color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.padding(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolAction("下次放行一次", Modifier.weight(1f), true) { RuntimeStore.grant(activity, source, destination, true); grants = false; toast("已设置，请回原App重新操作；使用一次或5分钟后失效") }
                    ToolAction("放行5分钟", Modifier.weight(1f)) { RuntimeStore.grant(activity, source, destination, false); grants = false; toast("已临时允许5分钟") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolAction("撤销全部临时允许", Modifier.weight(1f)) { RuntimeStore.revokeGrants(activity); revision++; toast("临时允许已撤销") }
                    ToolAction("关闭", Modifier.weight(1f)) { grants = false }
                }
            }
        }
        GlassDialog(show = health, title = "兼容性体检", summary = "安装成功表示入口已匹配；功能是否执行成功，以运行记录为准。", onDismissRequest = { health = false }) {
            Column {
                Text(healthText(), color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.body2, modifier = Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolAction("关闭", Modifier.weight(1f)) { health = false }
                    ToolAction("查看运行记录", Modifier.weight(1f), true) { health = false; details() }
                }
            }
        }
    }
    private fun healthText() = buildString {
        for ((pkg, label) in listOf(StatusController.Q to "胖乖生活", StatusController.Z to "智趣校园")) {
            append("$label\n")
            val record = RuntimeStore.json(activity, "health.$pkg")
            val steps = record.optJSONArray("steps")
            if (steps == null) { append("暂无主进程安装记录，请重启目标App。\n\n"); continue }
            append("本地配置：${if (record.optBoolean("localConfig")) "已同步" else "未确认"}\n")
            if (pkg == StatusController.Q) {
                for ((entry, title) in listOf("Auto scan" to "设备识别", "Device default selections" to "机器与档位", "Order default selections" to "支付与权益", "Auto advance" to "推进到预览")) {
                    val step = (0 until steps.length()).mapNotNull { steps.optJSONObject(it) }.firstOrNull { it.optString("name") == entry }
                    append("$title：${when { step == null -> "无记录"; step.optBoolean("installed") -> "入口已安装"; else -> "未匹配，需适配" }}\n")
                }
                append("安装时模式：${LocalData.modeLabel(record.optString("mode"))}\n")
            }
            val installed = (0 until steps.length()).count { steps.optJSONObject(it)?.optBoolean("installed") == true }
            append("全部入口：$installed 项已安装，${steps.length() - installed} 项未匹配或可选入口缺失\n\n")
        }
        append("仅去广告模式即使安装了兼容入口，也不会执行设备识别、预选或自动推进。目标版本更新后需重新验收。")
    }
    private fun money(cents: Long) = String.format(java.util.Locale.CHINA, "%.2f元", cents / 100.0)
    fun save(out: android.os.Bundle) {
        out.putBoolean("safety", safety); out.putBoolean("health", health); out.putBoolean("grants", grants); out.putBoolean("accept", accept)
        out.putString("source", source); out.putString("destination", destination)
    }
}
