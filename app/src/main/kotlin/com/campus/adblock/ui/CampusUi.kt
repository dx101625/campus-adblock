package com.campus.adblock.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.campus.adblock.Cfg
import com.campus.adblock.AutomationStore
import com.campus.adblock.R
import com.campus.adblock.SettingsActivity
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

internal val LocalPreferencesRevision = compositionLocalOf { 0 }

/** Uses the published Miuix 0.9.4 components, not look-alike Android widgets. */
class CampusUi(private val activity: SettingsActivity, saved: Bundle?) : AutoCloseable {
    private val ui = UiKit(activity)
    private var selected by mutableIntStateOf(saved?.getInt("ui.tab", 0) ?: 0)
    private var detail by mutableStateOf(saved?.getString("ui.detail"))
    private val detailPositions = saved?.getBundle("ui.detail.scroll") ?: Bundle()
    private var qrDraft by mutableStateOf(saved?.getString("ui.qr") ?: Cfg.uiString(activity, "q.qr", ""))
    private val positions = saved?.getIntArray("ui.scroll") ?: IntArray(3)
    private var qBeat by mutableStateOf(StatusController.Beat(0, ""))
    private var zBeat by mutableStateOf(StatusController.Beat(0, ""))
    private var checking by mutableStateOf(false)
    private var activation by mutableStateOf(FrameworkActivation.Snapshot.checking())
    private val framework = FrameworkActivation.getInstance()
    private val activationListener = FrameworkActivation.Listener { activation = it }
    private var showClear by mutableStateOf(false)
    private var qrError by mutableStateOf(false)
    private var bottomSpace by mutableIntStateOf(ui.dp(108))
    private lateinit var root: FrameLayout
    private lateinit var page: ComposeView
    private var bottomGap by mutableIntStateOf(ui.dp(12))
    private val version = try { "v" + activity.packageManager.getPackageInfo(activity.packageName, 0).versionName } catch (_: Exception) { "" }
    private val status = StatusController(activity) { q, z, busy ->
        qBeat = q; zBeat = z; checking = busy
    }
    private var dataRevision by mutableIntStateOf(0)
    private val tools = ModuleTools(activity, {
        qrDraft = Cfg.uiString(activity, "q.qr", ""); qrError = false; dataRevision++; status.refresh()
    }, saved?.getBundle("tools"))
    private val habits = HabitTools(activity, { dataRevision++ }, tools::showStatus, saved?.getBundle("habits"))

    fun createView(): View {
        framework.listen(activationListener)
        root = FrameLayout(activity).apply { clipChildren = false; clipToPadding = false; setBackgroundColor(ui.color("bg")) }
        page = ComposeView(activity).apply {
            id = R.id.page_host
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                CompositionLocalProvider(LocalPreferencesRevision provides dataRevision) {
                CampusTheme {
                    val revision = LocalPreferencesRevision.current
                    val clearGlass = remember(revision) { Cfg.uiString(activity, "ui.glass", "liquid") == "clear" }
                    val animations = remember(revision) { ui.animations() }
                    val backdrop = rememberLayerBackdrop()
                    LaunchedEffect(selected) { if (selected == 0) { status.refresh();framework.refresh() } }
                    Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    GlassDialogHost(backdrop, animations, ui.highContrast() || clearGlass) {
                        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { Pages() }
                        if (detail == null) GlassNavigation(
                            selectedIndex = selected,
                            onSelected = { if (selected != it && Cfg.uiBoolean(activity, "ui.haptics", true)) root.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); detail = null; selected = it },
                            backdrop = backdrop,
                            animations = animations,
                            highContrast = ui.highContrast() || clearGlass,
                            modifier = Modifier.align(Alignment.BottomCenter)
                                .padding(start = 16.dp, end = 16.dp,
                                    bottom = with(androidx.compose.ui.platform.LocalDensity.current) { bottomGap.toDp() })
                                .widthIn(max = 320.dp).fillMaxWidth().height(56.dp),
                        )
                    }
                    }
                }
                }
            }
        }
        root.addView(page, FrameLayout.LayoutParams(-1, -1))
        root.setOnApplyWindowInsetsListener { _, insets -> layout(insets); insets }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> layout(root.rootWindowInsets) }
        root.post { layout(root.rootWindowInsets) }
        return root
    }

    @Composable private fun CampusTheme(content: @Composable () -> Unit) {
        val type = remember {
            defaultTextStyles().let { t -> t.copy(
                main = t.main.copy(fontSize = 15.5.sp), paragraph = t.paragraph.copy(fontSize = 15.5.sp),
                body1 = t.body1.copy(fontSize = 15.sp), body2 = t.body2.copy(fontSize = 13.sp),
                button = t.button.copy(fontSize = 15.5.sp), footnote1 = t.footnote1.copy(fontSize = 12.sp),
                footnote2 = t.footnote2.copy(fontSize = 10.5.sp), headline1 = t.headline1.copy(fontSize = 15.5.sp),
                headline2 = t.headline2.copy(fontSize = 15.sp), subtitle = t.subtitle.copy(fontSize = 13.sp),
                title1 = t.title1.copy(fontSize = 28.sp), title2 = t.title2.copy(fontSize = 22.sp),
                title3 = t.title3.copy(fontSize = 19.sp), title4 = t.title4.copy(fontSize = 17.sp),
            ) }
        }
        val theme = remember(LocalPreferencesRevision.current) { Cfg.uiString(activity, "ui.theme", "system") }
        val dark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
        val colors = if (dark) darkColorScheme() else lightColorScheme()
        LaunchedEffect(dark) {
            activity.window.statusBarColor = colors.surface.toArgb(); activity.window.navigationBarColor = colors.surface.toArgb()
            var flags = if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26 && !dark) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            activity.window.decorView.systemUiVisibility = flags
            root.setBackgroundColor(colors.surface.toArgb())
        }
        MiuixTheme(colors = colors, textStyles = type, content = content)
    }

    private fun layout(insets: WindowInsets?) {
        if (root.width == 0) return
        val consumed = insets?.systemWindowInsetBottom ?: 0
        val gesture = when {
            insets == null -> 0
            Build.VERSION.SDK_INT >= 30 -> insets.getInsets(WindowInsets.Type.mandatorySystemGestures()).bottom
            Build.VERSION.SDK_INT >= 29 -> insets.mandatorySystemGestureInsets.bottom
            else -> 0
        }
        bottomGap = if (gesture > 0) ui.dp(8) + (gesture - consumed).coerceAtLeast(0) else ui.dp(12)
        bottomSpace = ui.dp(76) + bottomGap
    }

    @Composable private fun Pages() {
        BackHandler(enabled = detail != null) { detail = null }
        Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface).semantics { testTagsAsResourceId = true }) {
            Crossfade(targetState = detail ?: "tab_$selected", animationSpec = tween(if (ui.animations()) 160 else 0), label = "campus-pages") { route ->
                if (!route.startsWith("tab_")) DetailPage(route) else {
                val index = route.removePrefix("tab_").toIntOrNull()?.coerceIn(0, 2) ?: 0
                val scroll = rememberScrollState(positions[index])
                LaunchedEffect(scroll) {
                    snapshotFlow { scroll.value }.distinctUntilChanged().collect { positions[index] = it }
                }
                val width = if (LocalConfiguration.current.screenWidthDp >= 600) 560.dp else LocalConfiguration.current.screenWidthDp.dp
                Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = width).fillMaxWidth()) {
                        if (index == 2) {
                            Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                                Text(s("tab_about"), style = MiuixTheme.textStyles.headline1, color = MiuixTheme.colorScheme.onSurface)
                                Box(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).size(48.dp).clip(RoundedCornerShape(24.dp))
                                    .background(MiuixTheme.colorScheme.surfaceContainer).testTag("about_settings")
                                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "界面设置") { detail = "appearance" }, contentAlignment = Alignment.Center) {
                                    Image(painterResource(R.drawable.ic_settings), "界面设置", Modifier.size(24.dp), colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface))
                                }
                            }
                        } else if (index == 0) HomeHeader(activity, version, tools::showRelease) else Text(
                            s("tab_features"), style = MiuixTheme.textStyles.title1, color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp))
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            when (index) { 0 -> Home(); 1 -> Features(); else -> ModuleAbout(activity, version, { detail = "help" }, tools::feedback, tools::showRelease, { detail = "licenses" }) }
                            Spacer(Modifier.height(with(androidx.compose.ui.platform.LocalDensity.current) { bottomSpace.toDp() }))
                        }
                    }
                }
                }
            }
            ClearDialog()
            tools.Dialogs()
            habits.Dialogs()
        }
    }

    @Composable private fun DetailPage(route: String) {
        val title = when (route) {
            "habits" -> "使用习惯"; "devices" -> "设备码与常用设备"; "qiekj" -> "胖乖生活"
            "zqxy" -> "智趣校园"; "jumps" -> "广告与外跳"; "safety" -> "自动流程保护"
            "tools" -> "配置与诊断"; "appearance" -> "界面与交互"; "licenses" -> "引用与许可"; else -> "使用帮助"
        }
        val scroll = rememberScrollState(detailPositions.getInt(route, 0))
        LaunchedEffect(scroll, route) { snapshotFlow { scroll.value }.distinctUntilChanged().collect { detailPositions.putInt(route, it) } }
        Column(Modifier.fillMaxSize().testTag("page_$route").menuEnterMotion()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).testTag("detail_back").clip(RoundedCornerShape(24.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "返回") { detail = null }, contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.ic_back), "返回", Modifier.size(24.dp), colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurface))
                }
                Text(title, style = MiuixTheme.textStyles.title3, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 8.dp).weight(1f))
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    when (route) {
                        "habits" -> habits.Modes()
                        "devices" -> DevicesPage()
                        "qiekj" -> QiekjPage()
                        "zqxy" -> Section("广告与运营入口") { Setting("z.ads", true, s("ads"), s("z_ads_sub")); Divider(); Setting("z.tab", true, s("z_tab")) }
                        "jumps" -> JumpsPage()
                        "safety" -> { habits.Modes() }
                        "tools" -> ToolsPage()
                        "appearance" -> { Description("只调整模块界面；系统关闭动画或触感时，仍遵循系统设置。"); Section("界面与交互") { habits.Appearance() } }
                        "licenses" -> OpenSourcePage(activity)
                        else -> HelpPage()
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }

    @Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
        Text(title, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 8.dp))
        Card(modifier = Modifier.fillMaxWidth(), content = content)
    }

    @Composable private fun Description(text: String) {
        Text(text, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }

    @Composable private fun Divider() {
        Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(MiuixTheme.colorScheme.dividerLine))
    }

    @Composable private fun Action(label: String, primary: Boolean = false, modifier: Modifier = Modifier, enabled: Boolean = true, click: () -> Unit) {
        TextButton(text = label, onClick = click, modifier = modifier, enabled = enabled,
            minHeight = 48.dp, minWidth = 0.dp, insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            textStyle = MiuixTheme.textStyles.button.copy(fontSize = 13.5.sp, textAlign = TextAlign.Center),
            colors = if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors())
    }

    @Composable private fun Home() {
        HomeDashboard(activity, qBeat, zBeat, activation,
            onMode = { selected = 1; detail = "habits" }, onApp = { selected = 1; detail = it },
            onDevices = { selected = 1; detail = "devices" }, onStatus = tools::showStatus, onHealth = habits::showHealth)
    }

    @Composable private fun Status(name: String, beat: StatusController.Beat) {
        val active = beat.time > 0
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .15f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(8.dp)).background(Color(ui.color(if (active) "success" else "warn"))))
            Spacer(Modifier.width(9.dp))
            val detail = if (!active) s("status_missing") else s("status_active") + " · " + StatusController.ago(ui, beat.time) +
                if (beat.version.isEmpty()) "" else " · v" + beat.version.removePrefix("v")
            Text("$name · $detail", color = Color.White, style = MiuixTheme.textStyles.footnote1)
        }
    }

    @Composable private fun Setting(key: String, fallback: Boolean, title: String, summary: String? = null, inverted: Boolean = false) {
        var value by remember(key, LocalPreferencesRevision.current) { mutableStateOf(Cfg.uiBoolean(activity, key, fallback) xor inverted) }
        SwitchPreference(checked = value, onCheckedChange = { value = it; Cfg.uiPutBoolean(activity, key, it xor inverted); dataRevision++ },
            title = title, summary = summary, modifier = Modifier.testTag(key))
    }

    @Composable private fun Radios(key: String, fallback: String, labels: List<String>, values: List<String>) {
        var value by remember(key, LocalPreferencesRevision.current) { mutableStateOf(Cfg.uiString(activity, key, fallback)) }
        val title = when (key) { "q.machine" -> "默认机号"; "q.tier" -> "默认档位"; "q.pay" -> "支付偏好"; else -> "拦截强度" }
        PreferenceChoice(title, labels, values.indexOf(value).coerceAtLeast(0), key) { index ->
            value = values[index]; Cfg.uiPutString(activity, key, value); dataRevision++
        }
    }

    @Composable private fun Features() {
        val revision = LocalPreferencesRevision.current
        val mode = remember(revision) { AutomationStore.mode(activity.getSharedPreferences(Cfg.GROUP, 0)) }
        val count = remember(revision) { LocalData.devices(activity).size }
        Description("按类别调整，设置即时保存；重启目标App后生效。")
        Section("按自己的习惯") {
            Category("使用习惯", if (mode == "ads") "自动跳转已关闭 · 仅去广告" else "自动跳转已开启 · 直达订单预览", R.drawable.ic_tab_features, "habits")
            Divider(); Category("设备码与常用设备", "${count}台收藏 · 图片识码 · 默认设备", R.drawable.ic_scan, "devices")
        }
        Section("目标App") {
            Category("胖乖生活", "去广告、默认机号、档位与支付偏好", R.drawable.ic_device_hair, "qiekj")
            Divider(); Category("智趣校园", "去广告与底部运营入口", R.drawable.ic_device_water, "zqxy")
        }
        Section("拦截与保护") {
            Category("广告与外跳", "防摇一摇、广告外跳与允许的应用", R.drawable.ic_shield, "jumps")

        }
        Section("工具") { Category("配置与诊断", "备份恢复、兼容性体检与运行记录", R.drawable.ic_source_code, "tools") }
    }

    @Composable private fun Category(title: String, summary: String, icon: Int, route: String) {
        BasicComponent(title = title, summary = summary, modifier = Modifier.testTag("open_$route"),
            startAction = {
                val pkg = when (route) { "qiekj" -> StatusController.Q; "zqxy" -> StatusController.Z; else -> null }
                Box(Modifier.padding(end = 12.dp)) {
                    if (pkg != null) TargetIcon(rememberTarget(activity, pkg, 36.dp), if (route == "qiekj") "胖" else "趣", 36.dp)
                    else Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MiuixTheme.colorScheme.primary.copy(alpha = .09f)), contentAlignment = Alignment.Center) {
                        AppGlyph(icon, size = 21.dp, tint = MiuixTheme.colorScheme.primary)
                    }
                }
            }, endActions = { AppGlyph(R.drawable.ic_chevron_right, size = 18.dp, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary) }, onClick = { detail = route })
    }

    @Composable private fun DevicesPage() {
        Card(Modifier.fillMaxWidth()) {
            BasicComponent(title = "图片转设备码", summary = "选择二维码图片，本机识别后可复制或设为默认", modifier = Modifier.testTag("image_to_code"),
                startAction = { AppGlyph(R.drawable.ic_scan, Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.primary) }, endActions = { AppGlyph(R.drawable.ic_chevron_right, size = 18.dp, tint = MiuixTheme.colorScheme.primary) }, onClick = tools::importDeviceCode)
        }
        Text("常用设备", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(start = 12.dp, top = 18.dp, bottom = 8.dp))
        tools.Devices()
        Section(s("qr_title")) {
            Column(Modifier.padding(16.dp)) {
                Text(s("qr_hint"), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.height(12.dp))
                TextField(value = qrDraft, onValueChange = { qrDraft = it; qrError = false },
                    label = s("qr_placeholder"), useLabelAsPlaceholder = true, minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("qr_input"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                if (qrError) Text(s("qr_invalid"), color = MiuixTheme.colorScheme.error, style = MiuixTheme.textStyles.footnote1, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.padding(top = 12.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Action(s("paste"), modifier = Modifier.weight(1f).fillMaxHeight(), click = ::paste)
                    Action(s("save"), primary = true, modifier = Modifier.weight(1f).fillMaxHeight(), click = ::saveQr)
                    Action(s("clear"), modifier = Modifier.weight(1f).fillMaxHeight()) { showClear = true }
                }
            }
        }
    }

    @Composable private fun QiekjPage() {
        Description("只去广告时，机号、档位和支付全部由你操作；以下偏好用于自动流程。")
        Section(s("qiekj_section")) {
            Setting("q.ads", true, s("ads"), s("q_ads_sub")); Divider()
            Setting("q.splash", true, s("splash"), s("splash_sub"))
        }
        Section(s("machine_title")) { Radios("q.machine", "right2", listOf(s("machine_left"), s("machine_right"), s("machine_auto"), s("machine_none")), listOf("left1", "right2", "auto", "none")) }
        Section(s("tier_title")) { Radios("q.tier", "shortest", listOf(s("tier_short"), s("tier_long")), listOf("shortest", "longest")) }
        Section(s("order_title")) {
            Text(s("pay_title"), style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp))
            Radios("q.pay", "unionpay", listOf(s("pay_union"), s("pay_ali"), s("pay_wechat"), s("pay_none")), listOf("unionpay", "alipay", "wechat", "none"))
            Divider(); Setting("q.coupon", false, s("coupon"), "允许原App自动选择可用优惠券，仍遵循原有可用性校验")
            Divider(); Setting("q.points", false, s("points"), "允许原App自动选择可用积分，保留原有验证流程")
        }

    }

    @Composable private fun JumpsPage() {
        Description("减少误触，让广告留在原App内。")
        Section("跳转保护") {
            Setting("g.shake", true, "防止摇一摇跳转", "拦截广告使用的摇一摇传感器"); Divider()
            Setting("g.jump", true, "拦截广告外跳", "阻止名单中的推广应用被唤起"); Divider()
            BasicComponent(title = "允许打开的应用", summary = "需要正常跳转时，在这里添加例外",
                modifier = Modifier.testTag("jump_allowed"),
                endActions = { AppGlyph(R.drawable.ic_chevron_right, size = 18.dp, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary) }, onClick = tools::showAllowList)
        }
        Description("支付应用不在广告拦截名单中。修改后重启目标App生效。")
    }

    @Composable private fun ToolsPage() {
        Section("配置") {
            BasicComponent(title = "配置备份与恢复", summary = "本地导入导出，恢复前确认；设备码可选包含", onClick = tools::showBackup)
        }
        Section("诊断") {
            Setting("g.log", true, s("log")); Divider()
            BasicComponent(title = "兼容性体检", summary = "模块加载、入口匹配与运行记录分开查看", onClick = habits::showHealth)
            Divider(); BasicComponent(title = "运行详情", summary = "查看最近执行结果与脱敏诊断", onClick = tools::showStatus)
        }
        Description(s("restart_hint"))
    }

    @Composable private fun ClearDialog() {
        GlassDialog(show = showClear, title = s("clear"), summary = s("qr_clear_confirm"), onDismissRequest = { showClear = false }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Action(s("cancel"), modifier = Modifier.weight(1f)) { showClear = false }
                Action(s("clear"), primary = true, modifier = Modifier.weight(1f)) {
                    activity.getSharedPreferences(Cfg.GROUP, 0).edit().putString("q.qr", "").putString("q.profile", "").apply(); qrDraft = ""; qrError = false; showClear = false; toast("qr_cleared")
                }
            }
        }
    }

    private fun saveQr() {
        val value = qrDraft.trim()
        if (value.isEmpty() && Cfg.uiString(activity, "q.qr", "").isNotEmpty()) { showClear = true; return }
        if (value.isNotEmpty() && !validQr(value)) { qrError = true; toast("qr_invalid"); return }
        activity.getSharedPreferences(Cfg.GROUP, 0).edit().putString("q.qr", value).putString("q.profile", "").apply(); qrError = false; toast("saved")
    }

    private fun paste() {
        val manager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = manager.primaryClip
        val value = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(activity)?.toString()?.trim() else null
        if (value.isNullOrEmpty()) toast("clipboard_empty") else { qrDraft = value; qrError = false }
    }

    private fun validQr(value: String): Boolean = LocalData.validQr(value)

    private fun installed(pkg: String, verified: String) = try {
        val actual = activity.packageManager.getPackageInfo(pkg, 0).versionName
        "已安装 $actual · " + if (actual == verified) "当前版本已验证" else "当前版本尚未验证（已验证 $verified）"
    } catch (_: Exception) { s("not_installed") }

    private fun s(key: String) = ui.text(key)
    private fun toast(key: String) { Toast.makeText(activity, s(key), Toast.LENGTH_SHORT).show() }
    fun refresh() { dataRevision++; status.refresh();framework.refresh() }
    fun save(out: Bundle) {
        out.putInt("ui.tab", selected); out.putString("ui.qr", qrDraft.takeIf { it.length <= 8192 } ?: ""); out.putIntArray("ui.scroll", positions)
        out.putString("ui.detail", detail); out.putBundle("ui.detail.scroll", detailPositions)
        out.putBundle("tools", Bundle().also { tools.save(it) }); out.putBundle("habits", Bundle().also { habits.save(it) })
    }
    override fun close() { framework.remove(activationListener);status.close(); tools.close() }
}
