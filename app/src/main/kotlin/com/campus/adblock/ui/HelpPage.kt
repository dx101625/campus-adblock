package com.campus.adblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campus.adblock.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun HelpPage() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MiuixTheme.colorScheme.primaryContainer).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("三步开始使用", color = MiuixTheme.colorScheme.onPrimaryContainer, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Medium)
            Text("少一点广告，操作方式由你决定。", color = MiuixTheme.colorScheme.onPrimaryContainer, style = MiuixTheme.textStyles.body2)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                listOf("启用模块", "选使用习惯", "准备设备码").forEachIndexed { i, label ->
                    if (i > 0) AppGlyph(R.drawable.ic_chevron_right, Modifier.padding(horizontal = 6.dp), size = 16.dp, tint = MiuixTheme.colorScheme.onPrimaryContainer)
                    Text(label, color = MiuixTheme.colorScheme.onPrimaryContainer, style = MiuixTheme.textStyles.footnote1, modifier = Modifier.weight(1f))
                }
            }
        }
        HelpStep("01", "启用与作用域", "在LSPosed或兼容框架中启用本模块，勾选胖乖生活或智趣校园，再完全退出并重开目标App。KernelSU本身不代替LSPosed。")
        HelpStep("02", "选择适合自己的方式", "功能 → 使用习惯。只需一个自动跳转开关：开启直达订单预览，关闭后自己操作。")
        Card(Modifier.fillMaxWidth()) {
            listOf("关闭" to "只去广告，扫码、选机和后续步骤自己操作", "开启" to "自动识别并预选，直达订单预览；付款自己确认").forEachIndexed { i, pair ->
                Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                    Column { Text(pair.first, color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.body2); Text(pair.second, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1) }
                }
            }
        }
        HelpStep("03", "准备设备码", "功能 → 设备码与常用设备。可选择图片转设备码、粘贴链接，或先在胖乖里主动扫一次。识别结果可复制或设为默认，也能收藏为独立档案。图片在本机识别。")
        Text("常见问题 · 点击展开", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1, modifier = Modifier.padding(start = 10.dp, top = 8.dp))
        Card(Modifier.fillMaxWidth()) {
            HelpQuestion("manual", "平时手动，偶尔才想直达？", "需要直达时开启自动跳转，不需要时关闭。所有设备入口统一遵循这个开关，机号与档位仍按设备偏好选择。")
            HelpQuestion("shortcut", "怎样添加桌面设备入口？", "收藏并保存设备后，编辑这台设备，选择“添加到桌面”。桌面可能要求创建快捷方式权限。长按排序手柄可调整常用设备顺序，删除后短时间内可撤销。")
            HelpQuestion("paused", "自动跳转没有成功怎么办？", "自动流程只由功能 → 使用习惯中的开关控制。不需要自动跳转时关闭即可；识别遇到网络或设备异常时可手动操作，下次重新打开会重试。最终付款始终由你确认。")
            HelpQuestion("jump", "正常跳转被拦住了？", "功能 → 广告与外跳 → 允许打开的应用，开启需要正常跳转的应用即可。重启目标App后重新操作；也可以关闭广告外跳拦截。支付应用不在广告拦截名单中。")
            HelpQuestion("scope", "支持哪些App和设备？", "作用于胖乖生活、智趣校园独立App，不覆盖微信或支付宝内的小程序。自动选机、档位与推进目前在胖乖1.144.0吹风设备页验证，其他设备类型请先手动操作。")
            HelpQuestion("privacy", "图片、配置和付款如何处理？", "图片识别与设备档案保存在本机。设备查询和支付仍由原App处理。模块不代付、不创建订单、不启动设备；配置备份默认不包含设备码。")
        }
    }
}

@Composable private fun HelpStep(number: String, title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(MiuixTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text(number, color = MiuixTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(title, color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.headline1)
                Text(body, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2)
            }
        }
    }
}

@Composable private fun HelpQuestion(id: String, title: String, answer: String) {
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    BasicComponent(title = title, modifier = Modifier.testTag("help_$id"), endActions = { AppGlyph(if (expanded) R.drawable.ic_minus else R.drawable.ic_plus, size = 20.dp, tint = MiuixTheme.colorScheme.primary) }, onClick = { expanded = !expanded })
    val motion = LocalMenuMotion.current
    AnimatedVisibility(expanded,
        enter = expandVertically(animationSpec = if (motion) spring(dampingRatio = .68f, stiffness = 450f) else tween(0)) + fadeIn(tween(if (motion) 150 else 0)),
        exit = shrinkVertically(animationSpec = tween(if (motion) 140 else 0)) + fadeOut(tween(if (motion) 100 else 0))) {
        Text(answer, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp))
    }
}
