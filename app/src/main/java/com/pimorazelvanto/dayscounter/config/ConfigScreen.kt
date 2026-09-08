// MatchingDeclarationName: ConfigTestTags names the screen below, so it is read alongside it
// rather than from a file of its own.
@file:Suppress("MatchingDeclarationName")

package com.pimorazelvanto.dayscounter.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.ValidationResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Addressed by the instrumentation tests, so the strings are a contract, not an implementation detail. */
object ConfigTestTags {
    const val PREVIEW = "preview"
    const val TITLE_FIELD = "title_field"
    const val DATE_FIELD = "date_field"
    const val DATE_ERROR = "date_error"
    const val COLOR_FIELD = "color_field"
    const val COLOR_GRID = "color_grid"
    const val SAVE_BUTTON = "save_button"
    const val CANCEL_BUTTON = "cancel_button"

    fun colorOption(color: HeaderColor): String = "color_option_${color.name}"
}

private val SCREEN_PADDING = 24.dp
private val SECTION_SPACING = 20.dp
private val COLOR_DOT_SIZE = 32.dp
private const val MAX_YEARS_AHEAD = 100

// ParameterNaming: the callbacks keep the past-tense names of the ConfigViewModel methods they
// are bound to, and "onSaveFailureShown" has no sensible present-tense form.
@Suppress("ParameterNaming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfigScreen(
    state: ConfigUiState,
    onTitleChanged: (String) -> Unit,
    onTargetDateChanged: (LocalDate) -> Unit,
    onColorChanged: (HeaderColor) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onSaveFailureShown: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val previewDescription = stringResource(R.string.config_preview_description)

    SaveFailureSnackbar(state.saveState, snackbarHostState, onSaveFailureShown)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.config_screen_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(SCREEN_PADDING),
            verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
        ) {
            WidgetPreview(
                state = state.preview,
                modifier =
                    Modifier
                        .testTag(ConfigTestTags.PREVIEW)
                        .semantics { contentDescription = previewDescription },
            )
            TitleField(state, onTitleChanged)
            DateField(state, onClick = { showDatePicker = true })
            ColorField(state.color, onClick = { showColorPicker = true })
            ActionRow(
                isSaveEnabled = state.isValid && state.saveState != SaveState.Saving,
                onSave = onSave,
                onCancel = onCancel,
            )
        }
    }

    if (showDatePicker) {
        TargetDatePickerDialog(
            initialDate = state.targetDate,
            today = state.today,
            onConfirm = {
                showDatePicker = false
                onTargetDateChanged(it)
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showColorPicker) {
        ColorPickerDialog(
            selected = state.color,
            onSelect = {
                showColorPicker = false
                onColorChanged(it)
            },
            onDismiss = { showColorPicker = false },
        )
    }
}

@Suppress("ParameterNaming") // Mirrors the ConfigViewModel method name, as in ConfigScreen above.
@Composable
private fun SaveFailureSnackbar(
    saveState: SaveState,
    snackbarHostState: SnackbarHostState,
    onSaveFailureShown: () -> Unit,
) {
    val message = stringResource(R.string.config_save_failed)
    // Kept up to date rather than used as an effect key: a new lambda identity must not
    // restart the effect and show the snackbar a second time.
    val currentOnSaveFailureShown by rememberUpdatedState(onSaveFailureShown)
    LaunchedEffect(saveState) {
        if (saveState == SaveState.Failed) {
            snackbarHostState.showSnackbar(message)
            currentOnSaveFailureShown()
        }
    }
}

@Suppress("ParameterNaming") // Mirrors the ConfigViewModel method name, as in ConfigScreen above.
@Composable
private fun TitleField(
    state: ConfigUiState,
    onTitleChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = state.title,
        onValueChange = onTitleChanged,
        label = { Text(stringResource(R.string.config_label_title)) },
        supportingText = {
            Text(stringResource(R.string.config_title_length, state.title.length, ConfigViewModel.MAX_TITLE_LENGTH))
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag(ConfigTestTags.TITLE_FIELD),
    )
}

/**
 * The field itself is disabled so that it never takes focus or opens a keyboard; the
 * `clickable` modifier around it is what opens the picker, and the colours undo the
 * greyed-out look a disabled field would otherwise have.
 */
@Composable
private fun DateField(
    state: ConfigUiState,
    onClick: () -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val isError = state.validation == ValidationResult.NotInFuture
    OutlinedTextField(
        value = state.targetDate?.format(formatter) ?: "",
        onValueChange = {},
        readOnly = true,
        enabled = false,
        isError = isError,
        label = { Text(stringResource(R.string.config_label_target_date)) },
        placeholder = { Text(stringResource(R.string.config_pick_date)) },
        colors =
            OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        supportingText =
            if (isError) {
                {
                    Text(
                        text = stringResource(R.string.config_error_date_not_in_future),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(ConfigTestTags.DATE_ERROR),
                    )
                }
            } else {
                null
            },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(ConfigTestTags.DATE_FIELD),
    )
}

@Composable
private fun ColorField(
    color: HeaderColor,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(ConfigTestTags.COLOR_FIELD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SECTION_SPACING),
    ) {
        Text(stringResource(R.string.config_label_color), style = MaterialTheme.typography.bodyLarge)
        Box(Modifier.size(COLOR_DOT_SIZE).clip(CircleShape).background(Color(color.argb)))
        Text(stringResource(color.displayNameRes()), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ActionRow(
    isSaveEnabled: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onCancel, modifier = Modifier.testTag(ConfigTestTags.CANCEL_BUTTON)) {
            Text(stringResource(R.string.config_action_cancel))
        }
        Button(onClick = onSave, enabled = isSaveEnabled, modifier = Modifier.testTag(ConfigTestTags.SAVE_BUTTON)) {
            Text(stringResource(R.string.config_action_save))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetDatePickerDialog(
    initialDate: LocalDate?,
    today: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstSelectable = today.plusDays(1)
    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = initialDate?.toUtcStartOfDayMillis(),
            initialDisplayedMonthMillis = (initialDate ?: firstSelectable).withDayOfMonth(1).toUtcStartOfDayMillis(),
            yearRange = today.year..today.year + MAX_YEARS_AHEAD,
            selectableDates = FutureOnlySelectableDates(today),
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { datePickerState.selectedDateMillis?.let { onConfirm(utcMillisToLocalDate(it)) } },
                enabled = datePickerState.selectedDateMillis != null,
            ) { Text(stringResource(R.string.config_action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.config_action_cancel)) }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}
