package com.safeme.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.safeme.app.ui.theme.LocalAppColors

/**
 * The single shared text field for the whole app.
 *
 * Centralizes the two colors every input must get right against the app theme:
 *  - **cursor**: [BasicTextField]'s default `cursorBrush` is a hard-coded black
 *    (`SolidColor(Color.Black)`), which is invisible/wrong on the dark surface.
 *    Here it defaults to the theme's `ink` — near-white in dark mode, near-black
 *    in light mode — so the caret always matches the text color.
 *  - **text selection**: [BasicTextField] otherwise falls back to the framework's
 *    Google-blue selection highlight; this wraps the field in
 *    [LocalTextSelectionColors] so the highlight and handle use the brand color
 *    consistently everywhere.
 *
 * Every text input in the app must go through this component instead of calling
 * `BasicTextField` directly, so the cursor/selection colors stay theme-driven
 * and can never regress.
 */
@Composable
fun SafeMeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    textStyle: TextStyle = TextStyle(color = LocalAppColors.current.ink),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    interactionSource: MutableInteractionSource? = null,
    decorationBox: (@Composable (innerTextField: @Composable () -> Unit) -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val selectionColors = remember(colors) {
        TextSelectionColors(
            handleColor = colors.brand,
            backgroundColor = colors.brand.copy(alpha = 0.4f),
        )
    }
    CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.ink),
            keyboardOptions = keyboardOptions,
            interactionSource = interactionSource,
            decorationBox = decorationBox ?: { it() },
        )
    }
}
