package com.campus.adblock.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.campus.adblock.Cfg
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.UUID

data class DeviceOptions(val mode: String = "inherit", val machine: String = "inherit", val tier: String = "inherit",
                         val pay: String = "inherit", val coupon: String = "inherit", val points: String = "inherit")
data class FavoriteDevice(val id: String, val name: String, val qr: String, val group: String = "", val icon: String = "other", val options: DeviceOptions = DeviceOptions())
data class RestorePlan(val values: Map<String, Any>, val includesDevices: Boolean)

/** Own-process local data. QR text is excluded from diagnostics and optional in backups. */
object LocalData {
    const val FAVORITES = "ui.devices"
    private val booleans = mapOf("q.ads" to true, "q.splash" to true, "q.scan" to true,
        "q.advance" to true, "q.coupon" to false, "q.points" to false, "z.ads" to true,
        "z.tab" to true, "g.shake" to true, "g.jump" to true, "g.log" to true,
        "q.price.guard" to true, "q.pause" to true, "ui.haptics" to true, "ui.motion" to true)
    private val choices = mapOf("q.machine" to listOf("left1", "right2", "auto", "none"),
        "q.tier" to listOf("shortest", "longest"), "q.pay" to listOf("unionpay", "alipay", "wechat", "none"),
        "g.jump.mode" to listOf("standard", "strict"), "q.mode" to listOf("ads", "device", "preview"),
        "q.launch" to listOf("direct", "shortcut"), "ui.theme" to listOf("system", "light", "dark"), "ui.glass" to listOf("liquid", "clear"))
    private val defaults = mapOf("q.machine" to "right2", "q.tier" to "shortest", "q.pay" to "unionpay", "g.jump.mode" to "standard",
        "q.mode" to "preview", "q.launch" to "direct", "ui.theme" to "system", "ui.glass" to "liquid")
    val modeValues = listOf("inherit", "ads", "device", "preview")
    val modeLabels = listOf("跟随全局", "仅去广告", "停在设备页", "直达订单预览")
    val icons = listOf("hair", "washer", "water", "other")
    fun modeLabel(mode: String) = modeLabels.getOrElse(modeValues.indexOf(mode)) { "跟随全局" }
    fun iconLabel(icon: String) = when (icon) { "hair" -> "吹风"; "washer" -> "洗衣"; "water" -> "饮水"; else -> "设备" }
    fun validQr(text: String): Boolean = try {
        val uri = java.net.URI(text)
        text.length <= 8192 && uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() &&
            uri.rawQuery?.split('&')?.any { it.matches(Regex("(NQT|IMEI|SN)=[^&#]+")) } == true
    } catch (_: Exception) { false }

    fun devices(context: Context): List<FavoriteDevice> = try {
        parseDevices(JSONArray(Cfg.uiString(context, FAVORITES, "[]")))
    } catch (_: Exception) { emptyList() }
    private fun parseDevices(array: JSONArray): List<FavoriteDevice> {
        require(array.length() <= 30) { "最多保存30个常用设备" }
        val ids = mutableSetOf<String>()
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val id = item.getString("id"); val name = item.getString("name").trim(); val qr = item.getString("qr").trim()
            require(id.matches(Regex("[a-zA-Z0-9_-]{1,64}")) && ids.add(id) && name.length in 1..32 && !name.any { it.isISOControl() } && validQr(qr)) { "设备收藏格式不正确" }
            val group = if (item.has("group")) item.getString("group").trim() else ""
            val icon = if (item.has("icon")) item.getString("icon") else "other"
            require(group.length <= 24 && !group.any { it.isISOControl() } && icon in icons) { "设备分组或图标无效" }
            val o = if (item.has("options")) item.getJSONObject("options") else JSONObject()
            val keys = o.keys().asSequence().toSet()
            require(keys.all { it in listOf("mode", "machine", "tier", "pay", "coupon", "points") }) { "设备档案包含未知设置" }
            fun option(key: String, valid: List<String>): String {
                val value = if (o.has(key)) o.getString(key) else "inherit"
                require(value in valid) { "设备${key}设置无效" }; return value
            }
            val options = DeviceOptions(option("mode", modeValues), option("machine", listOf("inherit", "left1", "right2", "auto", "none")),
                option("tier", listOf("inherit", "shortest", "longest")), option("pay", listOf("inherit", "unionpay", "alipay", "wechat", "none")),
                option("coupon", listOf("inherit", "yes", "no")), option("points", listOf("inherit", "yes", "no")))
            FavoriteDevice(id, name, qr, group, icon, options)
        }
    }
    fun deviceJson(items: List<FavoriteDevice>) = JSONArray().apply {
        items.forEach { d -> put(JSONObject().put("id", d.id).put("name", d.name).put("qr", d.qr).put("group", d.group).put("icon", d.icon)
            .put("options", JSONObject().put("mode", d.options.mode).put("machine", d.options.machine).put("tier", d.options.tier)
                .put("pay", d.options.pay).put("coupon", d.options.coupon).put("points", d.options.points))) }
    }.toString()
    fun saveDevice(context: Context, previous: FavoriteDevice?, name: String, qr: String, group: String = "", icon: String = "other", options: DeviceOptions = DeviceOptions()) {
        require(name.trim().length in 1..32 && validQr(qr.trim())) { "请填写设备名称和有效设备码" }
        val items = devices(context).toMutableList()
        val item = FavoriteDevice(previous?.id ?: UUID.randomUUID().toString(), name.trim(), qr.trim(), group.trim(), icon, options)
        parseDevices(JSONArray(deviceJson(listOf(item))))
        val index = items.indexOfFirst { it.id == item.id }
        if (index < 0) { require(items.size < 30) { "最多保存30个常用设备" }; items.add(item) } else items[index] = item
        val editor = context.getSharedPreferences(Cfg.GROUP, Context.MODE_PRIVATE).edit().putString(FAVORITES, deviceJson(items))
        if (previous != null && (Cfg.uiString(context, "q.profile", "") == previous.id || (Cfg.uiString(context, "q.profile", "").isEmpty() && Cfg.uiString(context, "q.qr", "") == previous.qr))) editor.putString("q.qr", item.qr).putString("q.profile", item.id)
        check(editor.commit()) { "设备未保存，请重试" }
        com.campus.adblock.ProfileShortcuts.publish(context)
    }
    fun deleteDevice(context: Context, id: String) {
        val edit = context.getSharedPreferences(Cfg.GROUP, 0).edit().putString(FAVORITES, deviceJson(devices(context).filterNot { it.id == id }))
        if (Cfg.uiString(context, "q.profile", "") == id) edit.putString("q.profile", "")
        edit.commit(); com.campus.adblock.ProfileShortcuts.publish(context)
    }
    fun saveOrder(context: Context, items: List<FavoriteDevice>) { Cfg.uiPutString(context, FAVORITES, deviceJson(items)); com.campus.adblock.ProfileShortcuts.publish(context) }
    fun restoreDevice(context: Context, device: FavoriteDevice, index: Int) {
        val items = devices(context).toMutableList(); if (items.none { it.id == device.id } && items.size < 30) items.add(index.coerceIn(0, items.size), device)
        saveOrder(context, items)
        if (Cfg.uiString(context, "q.qr", "") == device.qr) Cfg.uiPutString(context, "q.profile", device.id)
    }
    fun select(context: Context, device: FavoriteDevice) { context.getSharedPreferences(Cfg.GROUP, 0).edit().putString("q.qr", device.qr).putString("q.profile", device.id).commit() }

    fun export(context: Context, includeDevices: Boolean): String {
        val values = JSONObject()
        booleans.forEach { (key, fallback) -> values.put(key, Cfg.uiBoolean(context, key, fallback)) }
        choices.forEach { (key, _) -> values.put(key, if (key == "q.mode") com.campus.adblock.AutomationStore.mode(context.getSharedPreferences(Cfg.GROUP, 0)) else Cfg.uiString(context, key, defaults.getValue(key))) }
        values.put("g.jump.allowed", Cfg.uiString(context, "g.jump.allowed", ""))
        if (includeDevices) {
            values.put("q.qr", Cfg.uiString(context, "q.qr", ""))
            values.put("q.profile", Cfg.uiString(context, "q.profile", ""))
            values.put(FAVORITES, JSONArray(deviceJson(devices(context))))
        }
        return JSONObject().put("format", "campus-adblock-config").put("schema", 2)
            .put("includesDevices", includeDevices).put("values", values).toString(2)
    }
    fun parseBackup(text: String): RestorePlan {
        require(text.length <= 1_048_576) { "备份文件过大" }
        val document = JSONObject(text)
        require(document.optString("format") == "campus-adblock-config" && document.optInt("schema") in 1..2) { "不是支持的模块配置备份" }
        val include = document.getBoolean("includesDevices")
        val values = document.getJSONObject("values"); val result = linkedMapOf<String, Any>()
        val keys = values.keys()
        while (keys.hasNext()) {
            val key = keys.next(); val value = values.get(key)
            when {
                key in booleans -> { require(value is Boolean) { "$key 类型错误" }; result[key] = value }
                key in choices -> { require(value is String && value in choices.getValue(key)) { "$key 值错误" }; result[key] = value }
                key == "g.jump.allowed" -> {
                    require(value is String && value.length <= 4096 && (value.isEmpty() || value.split(',').all { it.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+")) })) { "允许列表格式错误" }
                    result[key] = value
                }
                key == "q.qr" -> { require(include && value is String && (value.isEmpty() || validQr(value))) { "默认设备码无效" }; result[key] = value }
                key == "q.profile" -> { require(include && value is String && value.length <= 64) { "默认设备档案无效" }; result[key] = value }
                key == FAVORITES -> { require(include && value is JSONArray) { "设备收藏格式错误" }; result[key] = deviceJson(parseDevices(value)) }
                else -> error("备份包含不支持的设置：$key")
            }
        }
        require(result.isNotEmpty()) { "备份中没有设置" }
        return RestorePlan(result, include)
    }
    fun restore(context: Context, plan: RestorePlan) {
        val edit = context.getSharedPreferences(Cfg.GROUP, Context.MODE_PRIVATE).edit()
        if ("q.mode" !in plan.values && ("q.scan" in plan.values || "q.advance" in plan.values)) edit.remove("q.mode")
        if (plan.includesDevices && "q.profile" !in plan.values) edit.remove("q.profile")
        plan.values.forEach { (key, value) -> if (value is Boolean) edit.putBoolean(key, value) else edit.putString(key, value as String) }
        check(edit.commit()) { "恢复未完成，原配置未确认写入" }
        com.campus.adblock.ProfileShortcuts.publish(context)
    }
    fun readSmall(input: InputStream): ByteArray = input.use {
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val count = it.read(buffer); if (count < 0) break
            require(out.size() + count <= 1_048_576) { "备份文件过大" }; out.write(buffer, 0, count) }
        out.toByteArray()
    }
    fun decodeImage(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取这张图片" }
        val options = BitmapFactory.Options().apply { inSampleSize = 1
            while (bounds.outWidth / inSampleSize > 2048 || bounds.outHeight / inSampleSize > 2048) inSampleSize *= 2 }
        val bitmap = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) } ?: error("图片解码失败")
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            var source: com.google.zxing.LuminanceSource = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            val hints = mapOf(DecodeHintType.TRY_HARDER to true)
            repeat(4) {
                for (candidate in listOf(source, source.invert())) {
                    val result = runCatching { QRCodeReader().decode(BinaryBitmap(HybridBinarizer(candidate)), hints).text }.getOrNull()
                    if (result != null) { require(validQr(result.trim())) { "图片中的二维码不是设备码" }; return result.trim() }
                }
                if (source.isRotateSupported) source = source.rotateCounterClockwise()
            }
            error("没有识别到二维码，请选择清晰的原图")
        } finally { bitmap.recycle() }
    }
}
