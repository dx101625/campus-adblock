package com.campus.adblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.campus.adblock.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** One readable value per row; the picker uses color, a checkmark and radio semantics. */
@Composable internal fun PreferenceChoice(title: String, labels: List<String>, selected: Int, tag: String, onSelect: (Int) -> Unit) {
    var expanded by rememberSaveable(tag) { mutableStateOf(false) }
    val index = selected.coerceIn(labels.indices)
    BasicComponent(title = title, summary = "当前：${labels[index]}", modifier = Modifier.testTag(tag),
        endActions = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppGlyph(R.drawable.ic_chevron_right, size = 18.dp, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }, onClick = { expanded = true })
    GlassDialog(show = expanded, title = title, onDismissRequest = { expanded = false }) {
        val height = minOf(420.dp, LocalConfiguration.current.screenHeightDp.dp * .55f)
        Column(Modifier.semantics { testTagsAsResourceId = true }) {
            Column(Modifier.heightIn(max = height).verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                labels.forEachIndexed { option, label ->
                    val active = option == index
                    val fill = if (active) MiuixTheme.colorScheme.primary.copy(alpha = .13f) else Color.Transparent
                    val text = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                    Row(Modifier.fillMaxWidth().testTag("${tag}_$option").background(fill, RoundedCornerShape(16.dp))
                        .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(option); expanded = false })
                        .heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = text, fontWeight = if (active) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.weight(1f))
                        if (active) AppGlyph(R.drawable.ic_check, Modifier.padding(start = 12.dp), size = 20.dp, tint = text)
                    }
                }
            }
        }
    }
}
