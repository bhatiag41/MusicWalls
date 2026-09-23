package com.music.wallpaper.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.wallpaper.ui.theme.DarkBorder
import com.music.wallpaper.ui.theme.DarkSurfaceVariant
import com.music.wallpaper.ui.theme.LocalAppThemeColors
import com.music.wallpaper.ui.theme.Radius
import com.music.wallpaper.ui.theme.TextMuted
import com.music.wallpaper.ui.theme.TextPrimary
import com.music.wallpaper.ui.theme.TextSecondary

@Composable
fun SettingSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LocalAppThemeColors.current.textMuted,
        modifier = modifier.padding(bottom = 12.dp, top = 8.dp)
    )
}

@Composable
fun SettingSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueDisplay: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppThemeColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.accent,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = theme.accent,
                activeTrackColor = theme.accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
            )
        )
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppThemeColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = theme.accent,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.15f)
            )
        )
    }
}

@Composable
fun <T> SettingSegmentedRow(
    title: String,
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppThemeColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .background(DarkSurfaceVariant)
                .border(1.dp, DarkBorder, RoundedCornerShape(Radius.md))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) theme.accent else Color.Transparent,
                    label = "segBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else TextSecondary,
                    label = "segText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(bgColor)
                        .clickable { onItemSelected(item) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(item),
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSourcesDropdown(
    allApps: Map<String, String>,
    enabledApps: Set<String>,
    onToggleApp: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppThemeColors.current
    var expanded by remember { mutableStateOf(false) }

    // Summary text for closed dropdown
    val summaryText = remember(enabledApps, allApps) {
        when {
            enabledApps.size == allApps.size -> "All sources"
            enabledApps.isEmpty() -> "None"
            enabledApps.size == 1 -> allApps[enabledApps.first()] ?: "1 source"
            enabledApps.size == 2 -> enabledApps.mapNotNull { allApps[it] }.joinToString(", ")
            else -> "${enabledApps.size} sources selected"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Music sources",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = summaryText,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = theme.accent,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkSurfaceVariant,
                    unfocusedContainerColor = DarkSurfaceVariant
                ),
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(DarkSurfaceVariant)
            ) {
                allApps.forEach { (key, name) ->
                    val isChecked = enabledApps.contains(key)

                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = name,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (isChecked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = theme.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onToggleApp(key, !isChecked)
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppThemeColors.current

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = theme.accent,
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(Radius.md),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = TextPrimary
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}
