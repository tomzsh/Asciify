package com.asciify.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.asciify.studio.model.*
import java.util.Locale

private val inkColors = listOf(
    "Mint" to 0xFFB9F577, "White" to 0xFFF0F2ED, "Green" to 0xFF75FF8C,
    "Amber" to 0xFFFFBD69, "Cyan" to 0xFF85E8F4, "Blue" to 0xFFA0BBFF,
    "Pink" to 0xFFF5A3D2, "Coral" to 0xFFFF8D86,
).map { it.first to it.second.toInt() }
private val paperColors = listOf(
    "Black" to 0xFF000000, "Charcoal" to 0xFF0C0F10, "Navy" to 0xFF10243D,
    "Plum" to 0xFF26132B, "Forest" to 0xFF0E261C, "Slate" to 0xFF303A42,
    "Paper" to 0xFFE8E6D8, "White" to 0xFFFFFFFF,
).map { it.first to it.second.toInt() }
private fun hex(color: Int) = String.format(Locale.US, "#%06X", color and 0xFFFFFF)

@Composable fun ColorControls(settings: AsciiSettings, onSettings: (AsciiSettings) -> Unit) {
    val palette = settings.palette()
    val style = Styles.find(settings.styleId)
    val inkLabel = if (style.renderMode == RenderMode.DITHER) "Ink color" else "Character color"
    var customBackground by remember { mutableStateOf<Boolean?>(null) }
    StudioCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Set the mood", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onSettings(settings.copy(foregroundColor = null, backgroundColor = null, originalColor = false)) }) { Text("Style colors") }
        }
        Text("Choose your foreground and background colors. Both apply to the preview and exported video.",
            color = Muted, style = MaterialTheme.typography.bodySmall)
        ColorHeading(inkLabel, when (palette.mode) {
            ColorMode.ORIGINAL -> "From video"
            ColorMode.GRADIENT -> "Style gradient"
            ColorMode.MONO -> hex(palette.foreground)
        })
        ColorSwatches(inkColors, if (palette.mode == ColorMode.MONO) palette.foreground else null) {
            onSettings(settings.copy(foregroundColor = it, originalColor = false))
        }
        OutlinedButton(onClick = { customBackground = false }) { Text(if (style.renderMode == RenderMode.DITHER) "Custom ink HEX" else "Custom character HEX") }
        ToggleRow("Use source colors", "Keep the color from your original footage", palette.mode == ColorMode.ORIGINAL) {
            onSettings(settings.copy(originalColor = it,
                foregroundColor = if (!it && style.colorMode == ColorMode.ORIGINAL) settings.foregroundColor ?: style.foreground else settings.foregroundColor))
        }
        HorizontalDivider(color = Line)
        ColorHeading("Background", hex(palette.background))
        ColorSwatches(paperColors, palette.background) { onSettings(settings.copy(backgroundColor = it)) }
        OutlinedButton(onClick = { customBackground = true }) { Text("Custom background HEX") }
    }
    customBackground?.let { background ->
        var value by remember(background) { mutableStateOf(hex(if (background) palette.background else palette.foreground)) }
        val cleaned = value.trim().removePrefix("#")
        val parsed = if (cleaned.length == 6) cleaned.toLongOrNull(16)?.let { (it or 0xFF000000L).toInt() } else null
        AlertDialog(onDismissRequest = { customBackground = null },
            title = { Text(if (background) "Background color" else inkLabel) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = value, onValueChange = { if (it.length <= 7) value = it },
                        label = { Text("HEX color") }, placeholder = { Text("#B9F577") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        supportingText = { Text("Enter six hexadecimal digits, with an optional #.") })
                    parsed?.let { Box(Modifier.fillMaxWidth().height(40.dp).background(Color(it), MaterialTheme.shapes.small).border(1.dp, Line, MaterialTheme.shapes.small)) }
                }
            },
            confirmButton = { TextButton(enabled = parsed != null, onClick = {
                parsed?.let { onSettings(if (background) settings.copy(backgroundColor = it) else settings.copy(foregroundColor = it, originalColor = false)) }
                customBackground = null
            }) { Text("Apply color") } },
            dismissButton = { TextButton(onClick = { customBackground = null }) { Text("Cancel") } })
    }
}

@Composable private fun ColorHeading(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodySmall, color = Mint, fontFamily = FontFamily.Monospace)
    }
}

@Composable private fun ColorSwatches(colors: List<Pair<String, Int>>, selected: Int?, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        colors.forEach { (name, color) ->
            val active = color == selected
            Column(Modifier.width(56.dp).heightIn(min = 72.dp)
                .selectable(selected = active, onClick = { onSelect(color) }, role = Role.RadioButton)
                .semantics { contentDescription = "$name ${hex(color)}" }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(38.dp).border(if (active) 2.dp else 1.dp, if (active) Mint else Line, CircleShape)
                    .padding(4.dp).background(Color(color), CircleShape), contentAlignment = Alignment.Center) {
                    if (active) {
                        val light = AsciiMath.luminance(color shr 16 and 255, color shr 8 and 255, color and 255)
                        Icon(Icons.Outlined.Check, null, Modifier.size(20.dp), tint = if (light > 0.5f) Ink else Color.White)
                    }
                }
                Text(name, style = MaterialTheme.typography.labelSmall, color = if (active) Mint else Muted)
            }
        }
    }
}
