package com.campus.adblock.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.campus.adblock.Cfg
import com.campus.adblock.JumpPolicy
import com.campus.adblock.RuntimeEvents
import com.campus.adblock.SettingsActivity
import com.campus.adblock.ProfileShortcuts
import com.campus.adblock.AutomationStore
import kotlinx.coroutines.*
import org.json.JSONArray
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Own-process workflows: SAF files, offline decoding and explicit local confirmations. */
class ModuleTools(private val activity: SettingsActivity, private val onChanged: () -> Unit, saved: android.os.Bundle? = null) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var revision by mutableIntStateOf(0)
    private var editor by mutableStateOf(saved?.getBoolean("editor") ?: false)
    private var editing by mutableStateOf(LocalData.devices(activity).firstOrNull { it.id == saved?.getString("editing") })
    private var name by mutableStateOf(saved?.getString("name") ?: "")
    private var qr by mutableStateOf(saved?.getString("qr") ?: "")
    private var group by mutableStateOf(saved?.getString("group") ?: "")
    private var icon by mutableStateOf(saved?.getString("icon") ?: "other")
    private var options by mutableStateOf(DeviceOptions(saved?.getString("mode") ?: "inherit", saved?.getString("machine") ?: "inherit", saved?.getString("tier") ?: "inherit", saved?.getString("pay") ?: "inherit", saved?.getString("coupon") ?: "inherit", saved?.getString("points") ?: "inherit"))
    private var orderDraft by mutableStateOf<List<FavoriteDevice>?>(null)
    private var undo by mutableStateOf<Pair<FavoriteDevice, Int>?>(null)
    private var busy by mutableStateOf(false)
    private var error by mutableStateOf("")
    private var deleting by mutableStateOf(saved?.getBoolean("deleting") ?: false)
    private var backupDialog by mutableStateOf(saved?.getBoolean("backup") ?: false)
    private var includeDevices by mutableStateOf(saved?.getBoolean("includeDevices") ?: false)
    private var exportText = saved?.getString("export") ?: ""
    private var restorePlan by mutableStateOf<RestorePlan?>(null)
    private var allowList by mutableStateOf(false)
    private var statusDialog by mutableStateOf(false)
    private var releaseDialog by mutableStateOf(false)
    private var imageTarget by mutableStateOf(saved?.getString("imageTarget") ?: "favorite")
    private var decoded by mutableStateOf(saved?.getString("decoded") ?: "")
    private var decodedDialog by mutableStateOf(saved?.getBoolean("decodedDialog") ?: false)

    private val imagePicker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) work {
            val text = withContext(Dispatchers.IO) { LocalData.decodeImage(activity, uri) }
            if (imageTarget == "code") { decoded = text; decodedDialog = true; toast("已识别设备码") }
            else {
                qr = text; error = ""; editor = true
                if (name.isBlank()) name = "新设备"
                toast("已识别设备码，请确认名称后保存")
            }
        }
    }
    private val exporter = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) work {
            withContext(Dispatchers.IO) {
                val stream = activity.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入文件")
                stream.bufferedWriter(Charsets.UTF_8).use { it.write(exportText) }
            }
            toast("配置备份已保存")
        }
    }
    private val importer = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) work {
            restorePlan = withContext(Dispatchers.IO) {
                val stream = activity.contentResolver.openInputStream(uri) ?: error("无法读取文件")
                LocalData.parseBackup(LocalData.readSmall(stream).toString(Charsets.UTF_8))
            }
        }
    }
    private fun work(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            try { action() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = (e.message ?: "操作未完成，请重试").take(240); toast(error) }
            finally { busy = false }
        }
    }
    private fun changed() { revision++; onChanged() }
    private fun edit(device: FavoriteDevice?) {
        editing = device; name = device?.name ?: ""; qr = device?.qr ?: Cfg.uiString(activity, "q.qr", "")
        group = device?.group ?: ""; icon = device?.icon ?: "other"; options = device?.options ?: DeviceOptions()
        error = ""; editor = true
    }
    fun showStatus() { statusDialog = true }
    fun showRelease() { releaseDialog = true }
    fun showBackup() { includeDevices = false; backupDialog = true }
    fun showAllowList() { allowList = true }
    fun importDeviceCode() { imageTarget = "code"; error = ""; imagePicker.launch(arrayOf("image/*")) }
    fun feedback() {
        copyDiagnostic()
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/dx101625/campus-adblock/issues/new"))) }
            .onFailure { toast("无法打开浏览器，诊断信息已复制") }
    }

    @Composable fun Devices() {
        revision
        val devices = orderDraft ?: LocalData.devices(activity)
        val current = Cfg.uiString(activity, "q.qr", "")
        val currentId = Cfg.uiString(activity, "q.profile", "")
        Card(Modifier.fillMaxWidth()) {
            if (devices.isEmpty()) BasicComponent(title = "收藏常用设备", summary = "给设备码起个名字，切换时直接设为默认")
            devices.forEach { item -> key(item.id) {
                var rowHeight by remember { mutableFloatStateOf(240f) }
                val selected = if (currentId.isEmpty()) item.qr == current else currentId == item.id
                BasicComponent(title = item.name, summary = listOfNotNull(item.group.takeIf { it.isNotBlank() }, LocalData.iconLabel(item.icon), LocalData.modeLabel(item.options.mode), if (selected) "当前默认" else "点按设为默认").joinToString(" · "),
                    startAction = { ProfileSymbol(item.icon) },
                    modifier = Modifier.testTag("device_" + item.id).then(if (selected) Modifier.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier).onSizeChanged { rowHeight = it.height.toFloat().coerceAtLeast(1f) },
                    endActions = { Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (selected) AppGlyph(com.campus.adblock.R.drawable.ic_check, size = 19.dp, tint = MiuixTheme.colorScheme.primary)
                        ToolAction("编辑", Modifier.widthIn(min = 48.dp)) { edit(item) }
                        Box(Modifier.size(48.dp).testTag("sort_" + item.id).semantics { contentDescription = "长按拖动排序 ${item.name}" }.pointerInput(item.id) {
                            var moved = 0f
                            detectDragGesturesAfterLongPress(onDragStart = { orderDraft = LocalData.devices(activity); moved = 0f },
                                onDragCancel = { orderDraft = null }, onDragEnd = { orderDraft?.let { LocalData.saveOrder(activity, it) }; orderDraft = null; changed() }) { change, amount ->
                                change.consume(); moved += amount.y
                                if (kotlin.math.abs(moved) >= rowHeight * .7f) {
                                    val list = (orderDraft ?: LocalData.devices(activity)).toMutableList(); val at = list.indexOfFirst { it.id == item.id }
                                    val to = (at + if (moved > 0) 1 else -1).coerceIn(0, list.lastIndex)
                                    if (at >= 0 && to != at) { list.add(to, list.removeAt(at)); orderDraft = list }
                                    moved = 0f
                                }
                            }
                        }, contentAlignment = androidx.compose.ui.Alignment.Center) { Text("⋮⋮", color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
                    } },
                    onClick = { LocalData.select(activity, item); changed(); toast("已设为默认设备；普通打开遵循全局启动习惯") })
            } }
            undo?.let { deleted ->
                BasicComponent(title = "已删除 ${deleted.first.name}", endActions = { ToolAction("撤销", Modifier.widthIn(min = 48.dp)) { LocalData.restoreDevice(activity, deleted.first, deleted.second); undo = null; changed() } })
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolAction("添加设备", Modifier.weight(1f), true, !busy) { edit(null) }
                ToolAction(if (busy) "识别中…" else "从图片导入", Modifier.weight(1f), enabled = !busy) {
                    imageTarget = "favorite"; editing = null; name = ""; qr = ""; group = ""; icon = "other"; options = DeviceOptions(); imagePicker.launch(arrayOf("image/*"))
                }
            }
        }
    }

    @Composable fun Dialogs() {
        LaunchedEffect(undo) { if (undo != null) { delay(6000); undo = null } }
        val height = minOf(360.dp, LocalConfiguration.current.screenHeightDp.dp * .5f)
        GlassDialog(show = decodedDialog, title = "设备码已识别", summary = "图片仅在本机处理。可复制结果，或明确设为默认设备码。", onDismissRequest = { decodedDialog = false }) {
            Column(Modifier.semantics { testTagsAsResourceId = true }) {
                SelectionContainer { Text(decoded, color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.footnote1, modifier = Modifier.fillMaxWidth().heightIn(max = height).verticalScroll(rememberScrollState()).testTag("decoded_device_code")) }
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolAction("复制设备码", Modifier.weight(1f)) { (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("设备码", decoded)); toast("设备码已复制") }
                    ToolAction("设为默认设备码", Modifier.weight(1f), true) {
                        activity.getSharedPreferences(Cfg.GROUP, 0).edit().putString("q.qr", decoded).putString("q.profile", "").apply()
                        decodedDialog = false; changed(); toast("已保存默认设备码，重启目标App后生效")
                    }
                }
                ToolAction("关闭", Modifier.fillMaxWidth()) { decodedDialog = false }
            }
        }
        GlassDialog(show = editor, title = if (editing == null) "添加常用设备" else "编辑常用设备", onDismissRequest = { editor = false }) {
            Column(Modifier.semantics { testTagsAsResourceId = true }.heightIn(max = height).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(value = name, onValueChange = { name = it.take(32) }, label = "设备名称", useLabelAsPlaceholder = true, modifier = Modifier.testTag("favorite_name"))
                TextField(value = qr, onValueChange = { qr = it; error = "" }, label = "设备码链接", useLabelAsPlaceholder = true, minLines = 2, modifier = Modifier.testTag("favorite_qr"))
                TextField(value = group, onValueChange = { group = it.take(24) }, label = "分组（可选，如宿舍、洗衣房）", useLabelAsPlaceholder = true, modifier = Modifier.testTag("favorite_group"))
                ProfileChoice("设备图标", icon, LocalData.icons, listOf("吹风", "洗衣", "饮水", "其他"), "profile.icon") { icon = it }
                ProfileOptionsForm(options) { options = it }
                if (error.isNotBlank()) Text(error, color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.footnote1)
                editing?.let { saved ->
                    val dirty = saved.name != name.trim() || saved.qr != qr.trim() || saved.group != group.trim() || saved.icon != icon || saved.options != options
                    Text(if (dirty) "保存更改后，可使用或添加桌面入口。" else "设备入口每次读取最新保存的档案，付款仍由你确认。", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote2)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolAction("打开此设备", Modifier.weight(1f), enabled = !dirty) { ProfileShortcuts.launch(activity, saved.id) }
                        ToolAction("添加到桌面", Modifier.weight(1f), enabled = !dirty) { ProfileShortcuts.pin(activity, saved.id) }
                    }
                    ToolAction("删除收藏", Modifier.fillMaxWidth()) { deleting = true }
                }
            }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolAction("取消", Modifier.weight(1f)) { editor = false }
                    ToolAction("保存设备", Modifier.weight(1f), true) {
                        runCatching { LocalData.saveDevice(activity, editing, name, qr, group, icon, options) }
                            .onSuccess { editor = false; changed(); toast("设备已收藏") }
                            .onFailure { error = it.message ?: "设备未保存" }
                    }
                }
        }
        GlassDialog(show = deleting, title = "删除这条收藏？", summary = "仅移除收藏；当前默认设备码仍保留，可在设备码设置中清空。", onDismissRequest = { deleting = false }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolAction("取消", Modifier.weight(1f)) { deleting = false }
                ToolAction("删除", Modifier.weight(1f), true) { editing?.let { undo = it to LocalData.devices(activity).indexOfFirst { d -> d.id == it.id }; LocalData.deleteDevice(activity, it.id) }; deleting = false; editor = false; changed() }
            }
        }
        GlassDialog(show = backupDialog, title = "配置备份与恢复", onDismissRequest = { backupDialog = false }) {
            Column {
                SwitchPreference(checked = includeDevices, onCheckedChange = { includeDevices = it }, title = "备份中包含设备码", summary = "默认关闭。开启后，文件会包含默认设备码和设备收藏，请自行妥善保存。")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolAction("导入备份", Modifier.weight(1f), enabled = !busy) { backupDialog = false; importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                    ToolAction("导出备份", Modifier.weight(1f), true, !busy) {
                        exportText = LocalData.export(activity, includeDevices); backupDialog = false
                        exporter.launch("campus-adblock-config.json")
                    }
                }
                ToolAction("关闭", Modifier.fillMaxWidth()) { backupDialog = false }
            }
        }
        restorePlan?.let { plan ->
            GlassDialog(show = true, title = "确认恢复配置？", summary = "将覆盖文件中的${plan.values.size}项设置。" + if (plan.includesDevices) "包含设备码与收藏。" else "不改变当前设备码与收藏。", onDismissRequest = { restorePlan = null }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolAction("取消", Modifier.weight(1f)) { restorePlan = null }
                    ToolAction("恢复", Modifier.weight(1f), true) {
                        runCatching { LocalData.restore(activity, plan) }.onSuccess { restorePlan = null; changed(); toast("已恢复，重启目标App后生效") }.onFailure { toast(it.message ?: "恢复失败") }
                    }
                }
            }
        }
        GlassDialog(show = allowList, title = "允许打开的应用", summary = "开启后不再拦截到此应用的跳转，设置即时保存。", onDismissRequest = { allowList = false }) {
            Column {
                Column(Modifier.heightIn(max = height).verticalScroll(rememberScrollState())) {
                    var raw by remember(allowList) { mutableStateOf(Cfg.uiString(activity, "g.jump.allowed", "")) }
                    JumpPolicy.DESTINATIONS.forEach { (pkg, label) ->
                        SwitchPreference(checked = pkg in raw.split(','), onCheckedChange = { checked ->
                            val items = raw.split(',').filter { it.isNotBlank() }.toMutableSet()
                            if (checked) items.add(pkg) else items.remove(pkg)
                            raw = items.sorted().joinToString(","); Cfg.uiPutString(activity, "g.jump.allowed", raw); changed()
                        }, title = label)
                    }
                }
                ToolAction("完成", Modifier.fillMaxWidth(), true) { allowList = false }
            }
        }
        GlassDialog(show = statusDialog, title = "运行详情", summary = "加载记录表示模块已进入目标进程，具体功能以执行记录为准。", onDismissRequest = { statusDialog = false }) {
            Column {
                SelectionContainer { Text(diagnostic(), color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.heightIn(max = height).verticalScroll(rememberScrollState())) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolAction("关闭", Modifier.weight(1f)) { statusDialog = false }
                    ToolAction("复制诊断", Modifier.weight(1f), true) { copyDiagnostic() }
                }
            }
        }
        GlassDialog(show = releaseDialog, title = "更新记录", onDismissRequest = { releaseDialog = false }) {
            Column {
                Text("1.6.1\n• 精简广告外跳设置，保留常用开关与应用例外\n• 紧凑玻璃选择面板，浅蓝底与勾选显示当前值\n• 二级弹层统一实时背景模糊，支持叠层选择与返回\n\n1.6.0\n• 首页合并模块激活与使用方式\n• 实时读取LSPosed连接及作用域\n\n1.5.0\n• 全新首页：使用方式、双应用状态与最近执行\n• 胖乖与智趣使用系统真实图标\n• 统一Lucide线性图标与简洁盾牌标识\n• 大字体自适应应用卡片\n\n1.4.0\n• 功能页分组与二级设置，关于页新增界面设置入口\n• 图片直接转设备码，可复制或设为默认\n• 清晰的选中标记、分区帮助与引用许可页面\n• 玻璃导航加入速度相关的轻微回弹\n\n1.3.0\n• 仅去广告、停在设备页、直达订单预览三种习惯\n• 每设备独立档案、分组图标、排序与桌面入口\n• 一次或5分钟临时放行、连续失败暂停\n• 原生报价变化保护、兼容性体检\n• 深浅色与玻璃预览、删除撤销\n\n1.2.0\n• 常用设备收藏与本地二维码图片导入\n• 支付预选跟随设置，优惠券与积分限制独立生效\n• 自动识别失败停留当前页面\n• 运行摘要、诊断复制、严格拦截与允许列表\n• 配置备份恢复、紧凑选择与新版模块介绍\n\n1.1.14\n修复打包混淆导致自动直达回退扫码页的问题。",
                    color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.body2,
                    modifier = Modifier.heightIn(max = height).verticalScroll(rememberScrollState()))
                ToolAction("关闭", Modifier.fillMaxWidth(), true) { releaseDialog = false }
            }
        }

    }

    private fun diagnostic(): String = buildString {
        val version = runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }.getOrDefault("")
        append("校园设备去广告 $version\nAndroid ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\n机型 ${Build.MODEL}\n")
        for ((pkg, verified) in listOf(StatusController.Q to "1.144.0", StatusController.Z to "6.5.29")) {
            val installed = runCatching { activity.packageManager.getPackageInfo(pkg, 0).versionName }.getOrDefault("未安装")
            append("\n${if (pkg == StatusController.Q) "胖乖生活" else "智趣校园"}：$installed\n适配：${if (installed == verified) "当前版本已验证" else "当前版本尚未验证"}\n")
        }
        append("\n全局使用方式：${LocalData.modeLabel(AutomationStore.mode(activity.getSharedPreferences(Cfg.GROUP, 0)))}\n")
        append("\n当前全局预选：${Cfg.uiString(activity, "q.machine", "right2")} / ${Cfg.uiString(activity, "q.tier", "shortest")} / ${Cfg.uiString(activity, "q.pay", "unionpay")}\n")
        append("允许自动优惠券=${Cfg.uiBoolean(activity, "q.coupon", false)}，积分=${Cfg.uiBoolean(activity, "q.points", false)}\n")
        append("广告跳转=${Cfg.uiBoolean(activity, "g.jump", true)} / ${Cfg.uiString(activity, "g.jump.mode", "standard")}\n\n最近运行记录（仅本地保留30条）\n")
        val events = runCatching { JSONArray(activity.getSharedPreferences(RuntimeEvents.GROUP, Context.MODE_PRIVATE).getString("events", "[]")) }.getOrDefault(JSONArray())
        if (events.length() == 0) append("暂无执行记录；请开启模块日志并重启目标App。\n")
        val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)
        for (i in events.length() - 1 downTo 0) {
            val e = events.optJSONObject(i) ?: continue
            append("${format.format(Date(e.optLong("time")))} ${if (e.optString("pkg") == StatusController.Q) "胖乖" else "智趣"}\n${RuntimeEvents.clean(e.optString("detail"))}\n")
        }
        append("\n诊断不包含设备码、账号、订单号或支付信息。")
    }
    private fun copyDiagnostic() {
        (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("校园设备去广告诊断", diagnostic()))
        toast("诊断已复制，不含设备码和账号信息")
    }
    private fun toast(text: String) { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show() }
    fun save(out: android.os.Bundle) {
        out.putBoolean("editor", editor); out.putString("editing", editing?.id)
        out.putString("name", name); out.putString("qr", qr.takeIf { it.length <= 8192 } ?: "")
        out.putString("group", group); out.putString("icon", icon)
        out.putString("mode", options.mode); out.putString("machine", options.machine); out.putString("tier", options.tier)
        out.putString("pay", options.pay); out.putString("coupon", options.coupon); out.putString("points", options.points)
        out.putBoolean("deleting", deleting); out.putBoolean("backup", backupDialog); out.putBoolean("includeDevices", includeDevices)
        out.putString("export", exportText)
        out.putString("imageTarget", imageTarget); out.putString("decoded", decoded.takeIf { it.length <= 8192 } ?: ""); out.putBoolean("decodedDialog", decodedDialog)
    }
    override fun close() { scope.cancel() }
}

@Composable internal fun ToolAction(text: String, modifier: Modifier = Modifier, primary: Boolean = false,
                                   enabled: Boolean = true, action: () -> Unit) {
    TextButton(text, action, modifier = modifier, minHeight = 48.dp, minWidth = 0.dp, enabled = enabled,
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        textStyle = MiuixTheme.textStyles.body2.copy(textAlign = TextAlign.Center),
        colors = if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors())
}
