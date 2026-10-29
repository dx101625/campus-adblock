package com.campus.adblock.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.campus.adblock.R
import com.campus.adblock.SettingsActivity
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class LibraryInfo(
    val name: String, val version: String, val purpose: String,
    val license: String, val asset: String, val url: String,
)
private data class LicenseDocument(val title: String, val asset: String)

private val libraries = listOf(
    LibraryInfo("Miuix", "0.9.4", "为设置界面提供卡片、开关、单选和对话框。", "Apache-2.0", "licenses/MIUIX-APACHE-2.0.txt", "https://github.com/compose-miuix-ui/miuix"),
    LibraryInfo("Backdrop", "2.0.1", "为悬浮底栏与二级弹层提供实时模糊、折射与高光。", "Apache-2.0", "licenses/BACKDROP-APACHE-2.0.txt", "https://github.com/Kyant0/AndroidLiquidGlass"),
    LibraryInfo("Shapes", "1.2.1", "提供玻璃材质使用的圆角形状计算。", "Apache-2.0", "licenses/BACKDROP-APACHE-2.0.txt", "https://github.com/Kyant0/Shapes"),
    LibraryInfo("AndroidX / Compose", "Compose 1.12.0 · Activity 1.13.0", "提供界面渲染、Activity 和系统兼容支持。", "Apache-2.0", "licenses/MIUIX-APACHE-2.0.txt", "https://github.com/androidx/androidx"),
    LibraryInfo("Kotlin / kotlinx.coroutines", "Kotlin 2.4.20 · Coroutines 1.11.0", "提供界面代码和协程运行支持。", "Apache-2.0", "licenses/MIUIX-APACHE-2.0.txt", "https://github.com/JetBrains/kotlin"),
    LibraryInfo("GitHub Octicons", "mark-github-24", "提供 GitHub 小猫图标。", "MIT", "licenses/GITHUB-OCTICONS-MIT.txt", "https://github.com/primer/octicons"),
    LibraryInfo("Lucide", "24px SVG", "提供统一的导航、设备、状态与操作图标。包含 Feather 衍生图标的原始署名。", "ISC / MIT", "licenses/LUCIDE-LICENSE.txt", "https://github.com/lucide-icons/lucide"),
    LibraryInfo("ZXing Core", "3.5.3", "在本机识别二维码图片，不启动相机、不上传图片。", "Apache-2.0", "licenses/ZXING-APACHE-2.0.txt", "https://github.com/zxing/zxing"),
    LibraryInfo("libxposed Service", "102.0.0", "读取框架服务的实时连接、版本与模块作用域。", "Apache-2.0", "licenses/LIBXPOSED-SERVICE-APACHE-2.0.txt", "https://github.com/libxposed/service"),
)

/** Compact overview; acknowledgements live on their own page. */
@Composable
fun ModuleAbout(activity: SettingsActivity, version: String, onHelp: () -> Unit = {}, onFeedback: () -> Unit = {}, onRelease: () -> Unit = {}, onReferences: () -> Unit = {}) {
    val repo = stringResource(R.string.project_repository_url)
    val iconPx = with(LocalDensity.current) { 68.dp.roundToPx() }
    val icon = remember(iconPx) {
        val drawable = activity.packageManager.getApplicationIcon(activity.packageName)
        Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888).also { bitmap -> drawable.setBounds(0, 0, iconPx, iconPx); drawable.draw(Canvas(bitmap)) }.asImageBitmap()
    }
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(icon, null, Modifier.size(68.dp).testTag("about_module_icon"))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_name), style = MiuixTheme.textStyles.title2, color = MiuixTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Text(stringResource(R.string.about_version, version.removePrefix("v")), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.clickable(onClick = onRelease).padding(10.dp))
        Text("少一点广告，少几步操作。", style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
    AboutSection("关于模块") {
        Text(stringResource(R.string.about_description), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.padding(18.dp))
        AboutDivider()
        AboutRow("GitHub", repo.removePrefix("https://github.com/"), R.drawable.ic_github, tag = "project_github") { openAboutUrl(activity, repo) }
    }
    AboutSection("帮助与反馈") {
        AboutRow("使用帮助", "三步上手、使用方式与常见问题", R.drawable.ic_reference, tag = "about_help", onClick = onHelp)
        AboutDivider()
        AboutRow("反馈问题", "复制脱敏诊断，核对后由你提交", R.drawable.ic_source_code, tag = "about_feedback", onClick = onFeedback)
    }
    AboutSection("开源") {
        AboutRow("引用与许可", "Miuix、Backdrop与其他开源组件", R.drawable.ic_miuix, coloredIcon = true, tag = "about_references", onClick = onReferences)
    }
    Text("MIT License · Campus AdBlock", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onReferences).padding(vertical = 22.dp), textAlign = TextAlign.Center)
}

@Composable internal fun OpenSourcePage(activity: SettingsActivity) {
    var libraryName by rememberSaveable { mutableStateOf<String?>(null) }
    var licenseAsset by rememberSaveable { mutableStateOf<String?>(null) }
    var licenseTitle by rememberSaveable { mutableStateOf("") }
    val panelHeight = minOf(360.dp, LocalConfiguration.current.screenHeightDp.dp * .5f)
    Card(Modifier.fillMaxWidth()) {
        BasicComponent(title = "校园设备去广告", summary = "MIT License · 本项目源码许可", endActions = { AboutArrow() }, onClick = { licenseTitle = "Campus AdBlock · MIT"; licenseAsset = "licenses/CAMPUS-MIT.txt" })
    }
    AboutSection("开源组件") {
        libraries.forEachIndexed { index, item ->
            if (index > 0) AboutDivider()
            val icon = when (item.name) { "Miuix" -> R.drawable.ic_miuix; "Backdrop" -> R.drawable.ic_backdrop; "GitHub Octicons" -> R.drawable.ic_github; else -> R.drawable.ic_source_code }
            AboutRow(item.name, "${item.version} · ${item.license}", icon, coloredIcon = item.name in listOf("Miuix", "Backdrop"), tag = "library_$index") { libraryName = item.name }
        }
    }
    AboutSection("参考项目") {
        BasicComponent(title = "Miuix 液态玻璃导航", summary = "交互与玻璃透镜示例 · Apache-2.0", endActions = { AboutArrow() }, onClick = { openAboutUrl(activity, "https://github.com/compose-miuix-ui/miuix/blob/v0.9.4/example/shared/src/commonMain/kotlin/component/liquid/LiquidGlassNavigationBar.kt") })
        AboutDivider()
        BasicComponent(title = "Backdrop LiquidBottomTabs", summary = "实时折射与底栏示例 · Apache-2.0", endActions = { AboutArrow() }, onClick = { openAboutUrl(activity, "https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidBottomTabs.kt") })
    }
    libraries.firstOrNull { it.name == libraryName }?.let { item ->
        GlassDialog(show = true, title = "${item.name} ${item.version}", summary = item.purpose, onDismissRequest = { libraryName = null }) {
            Text(item.license, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AboutAction("项目主页", Modifier.weight(1f)) { openAboutUrl(activity, item.url) }
                AboutAction("开源许可", Modifier.weight(1f), true) { libraryName = null; licenseTitle = "${item.name} · ${item.license}"; licenseAsset = item.asset }
            }
            AboutAction("关闭", Modifier.fillMaxWidth()) { libraryName = null }
        }
    }
    licenseAsset?.let { asset ->
        GlassDialog(show = true, title = licenseTitle, onDismissRequest = { licenseAsset = null }) {
            Column {
                val text = remember(asset) { activity.assets.open(asset).bufferedReader().use { it.readText() } }
                SelectionContainer { Text(text, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.heightIn(max = panelHeight).verticalScroll(rememberScrollState())) }
                Spacer(Modifier.height(12.dp)); AboutAction("关闭", Modifier.fillMaxWidth(), true) { licenseAsset = null }
            }
        }
    }
}

@Composable private fun AboutSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 12.dp, top = 18.dp, bottom = 8.dp))
    Card(Modifier.fillMaxWidth(), content = content)
}

@Composable private fun AboutRow(title: String, summary: String, @DrawableRes icon: Int,
    coloredIcon: Boolean = false, tag: String, onClick: () -> Unit) {
    BasicComponent(title = title, summary = summary, modifier = Modifier.heightIn(min = 64.dp).testTag(tag),
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        startAction = {
            Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) {
                Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp),
                    colorFilter = if (coloredIcon) null else ColorFilter.tint(MiuixTheme.colorScheme.onSurface))
            }
        }, endActions = { AboutArrow() }, onClick = onClick)
}

@Composable private fun AboutArrow() {
    Image(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(18.dp),
        colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = .65f)))
}

@Composable private fun AboutDivider() {
    Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(.5.dp).background(MiuixTheme.colorScheme.dividerLine))
}

@Composable private fun AboutAction(text: String, modifier: Modifier, primary: Boolean = false, onClick: () -> Unit) {
    TextButton(text, onClick, modifier = modifier, minHeight = 48.dp, minWidth = 0.dp,
        textStyle = MiuixTheme.textStyles.button.copy(fontSize = MiuixTheme.textStyles.body2.fontSize, textAlign = TextAlign.Center),
        colors = if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors())
}

private fun openAboutUrl(activity: SettingsActivity, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme != "https" || uri.host.isNullOrBlank()) return
    try { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    catch (_: Exception) { Toast.makeText(activity, "未找到可以打开链接的应用", Toast.LENGTH_SHORT).show() }
}
