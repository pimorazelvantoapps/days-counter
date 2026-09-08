package com.pimorazelvanto.dayscounter.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.domain.HeaderColor

private const val GRID_COLUMNS = 3
private val SWATCH_SIZE = 48.dp
private val SWATCH_SPACING = 16.dp

/** Renders [HeaderColor.entries] in declaration order, which is the intended reading order. */
@Composable
internal fun ColorPickerDialog(
    selected: HeaderColor,
    onSelect: (HeaderColor) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.config_choose_color)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(SWATCH_SPACING),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .testTag(ConfigTestTags.COLOR_GRID),
            ) {
                HeaderColor.entries.chunked(GRID_COLUMNS).forEach { rowColors ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SWATCH_SPACING, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        rowColors.forEach { color ->
                            ColorSwatch(color, isSelected = color == selected, onClick = { onSelect(color) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.config_action_cancel)) }
        },
    )
}

/**
 * `selectable` rather than `clickable`, so that a screen reader announces which of the twelve
 * swatches is the chosen one; the check mark is the same statement in visual form and therefore
 * carries no description of its own.
 */
@Composable
private fun ColorSwatch(
    color: HeaderColor,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.config_color_description, stringResource(color.displayNameRes()))
    Box(
        modifier =
            Modifier
                .size(SWATCH_SIZE)
                .clip(CircleShape)
                .background(Color(color.argb))
                .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = description }
                .testTag(ConfigTestTags.colorOption(color)),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
        }
    }
}

internal fun HeaderColor.displayNameRes(): Int =
    when (this) {
        HeaderColor.RED -> R.string.color_name_red
        HeaderColor.ORANGE -> R.string.color_name_orange
        HeaderColor.YELLOW -> R.string.color_name_yellow
        HeaderColor.GREEN -> R.string.color_name_green
        HeaderColor.TEAL -> R.string.color_name_teal
        HeaderColor.BLUE -> R.string.color_name_blue
        HeaderColor.INDIGO -> R.string.color_name_indigo
        HeaderColor.PURPLE -> R.string.color_name_purple
        HeaderColor.PINK -> R.string.color_name_pink
        HeaderColor.BROWN -> R.string.color_name_brown
        HeaderColor.GREY -> R.string.color_name_grey
        HeaderColor.BLACK -> R.string.color_name_black
    }
