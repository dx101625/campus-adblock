package com.campus.adblock.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.campus.adblock.R
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable internal fun ProfileSymbol(icon: String) {
    val resource = when (icon) { "hair" -> R.drawable.ic_device_hair; "washer" -> R.drawable.ic_device_washer; "water" -> R.drawable.ic_device_water; else -> R.drawable.ic_device_other }
    Box(Modifier.width(38.dp)) { Image(painterResource(resource), null, Modifier.size(26.dp), colorFilter = ColorFilter.tint(MiuixTheme.colorScheme.primary)) }
}

@Composable internal fun ProfileOptionsForm(options: DeviceOptions, onChange: (DeviceOptions) -> Unit) {
    Text("独立偏好 · 跟随全局的项目会继承当前全局设置。仅去广告时不应用预选。", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
    ProfileChoice("使用方式", options.mode, LocalData.modeValues, listOf("全局", "去广告", "设备页", "预览"), "profile.mode") { onChange(options.copy(mode = it)) }
    ProfileChoice("机号", options.machine, listOf("inherit", "left1", "right2", "auto", "none"), listOf("全局", "左1", "右2", "自动", "手动"), "profile.machine") { onChange(options.copy(machine = it)) }
    ProfileChoice("档位", options.tier, listOf("inherit", "shortest", "longest"), listOf("全局", "最短", "最长"), "profile.tier") { onChange(options.copy(tier = it)) }
    ProfileChoice("支付方式", options.pay, listOf("inherit", "unionpay", "alipay", "wechat", "none"), listOf("全局", "银联", "支付宝", "微信", "原App"), "profile.pay") { onChange(options.copy(pay = it)) }
    ProfileChoice("自动使用优惠券", options.coupon, listOf("inherit", "yes", "no"), listOf("全局", "允许", "禁止"), "profile.coupon") { onChange(options.copy(coupon = it)) }
    ProfileChoice("自动使用积分", options.points, listOf("inherit", "yes", "no"), listOf("全局", "允许", "禁止"), "profile.points") { onChange(options.copy(points = it)) }
}

@Composable internal fun ProfileChoice(title: String, value: String, values: List<String>, labels: List<String>, tag: String, onChange: (String) -> Unit) {
    PreferenceChoice(title, labels, values.indexOf(value).coerceAtLeast(0), tag) { onChange(values[it]) }
}
