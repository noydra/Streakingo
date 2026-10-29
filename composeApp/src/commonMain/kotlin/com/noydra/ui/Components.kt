package com.noydra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

@Composable
fun AppText(
    text: String,
    size: Int,
    bold: Boolean = false,
    color: Color = Colors.text,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    fontFamily: FontFamily? = null,
) {
    Text(
        text, modifier = modifier, color = color, fontSize = scaledSp(size), textAlign = align, maxLines = maxLines,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontFamily = fontFamily,
        lineHeight = 1.3.em,
    )
}

@Composable
fun AppCard(modifier: Modifier = Modifier, padding: Dp = dimens.cardPadding, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(Colors.card).border(1.dp, Colors.border, shape).padding(padding),
        content = content,
    )
}

@Composable
private fun Pressable(
    onClick: () -> Unit,
    enabled: Boolean,
    color: Color,
    hoverColor: Color,
    shape: RoundedCornerShape,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .clip(shape)
            .background(if (enabled && (hovered || pressed)) hoverColor else color)
            .hoverable(interaction, enabled)
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 50.dp) {
    Pressable(onClick, enabled, Colors.primary, Colors.primaryHover, RoundedCornerShape(14.dp),
        modifier.fillMaxWidth().heightIn(min = height)) {
        FitText(text, 16, bold = true, color = Color.White, maxLines = 2,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, height: Dp = 38.dp, size: Int = 12) {
    Pressable(onClick, enabled, Colors.subtle, Colors.subtleHover, RoundedCornerShape(10.dp), modifier.heightIn(min = height)) {
        FitText(text, size, bold = true, maxLines = 2, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
fun IconButton(symbol: String, onClick: () -> Unit) {
    Pressable(onClick, true, Colors.subtle, Colors.subtleHover, RoundedCornerShape(12.dp), Modifier.size(42.dp)) {
        FitText(symbol, 20, fontFamily = FontFamily.Default)
    }
}

@Composable
fun Pill(text: String, color: Color, background: Color) {
    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(background).padding(horizontal = 12.dp, vertical = 6.dp)) {
        FitText(text, 12, bold = true, color = color)
    }
}

@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked, onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = Colors.primary,
            checkedThumbColor = Color.White,
            checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = Colors.switchOff,
            uncheckedThumbColor = Colors.switchKnob,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun DropdownPill(value: String, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Pressable({ open = true }, true, Colors.subtle, Colors.subtleHover, RoundedCornerShape(10.dp),
            Modifier.heightIn(min = 36.dp).widthIn(min = 58.dp)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AppText(value, 13, bold = true, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                AppText("▾", 12, color = Colors.muted, fontFamily = FontFamily.Default, maxLines = 1)
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.background(Colors.card)) {
            for (option in options) {
                DropdownMenuItem(
                    text = { AppText(option, 13, bold = option == value) },
                    onClick = { open = false; onSelect(option) },
                )
            }
        }
    }
}

@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, fontFamily: FontFamily? = null) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(10.dp)).background(Colors.subtle).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for ((key, label) in options) {
            val isSelected = key == selected
            Pressable({ onSelect(key) }, true, if (isSelected) Colors.segmentSelected else Color.Transparent,
                if (isSelected) Colors.segmentSelected else Colors.subtleHover, RoundedCornerShape(8.dp),
                Modifier.weight(1f).fillMaxHeight().heightIn(min = 34.dp)) {
                FitText(label, 13, bold = true, fontFamily = fontFamily,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
fun Modal(onDismiss: () -> Unit, maxWidth: Dp = 400.dp, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .safeDrawingPadding().padding(if (dimens.compact) 12.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier.widthIn(max = maxWidth).fillMaxWidth().clip(shape).background(Colors.bg)
                .border(1.dp, Colors.border, shape)
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState()).padding(dimens.modalPadding),
            content = content,
        )
    }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(Colors.border))
}
