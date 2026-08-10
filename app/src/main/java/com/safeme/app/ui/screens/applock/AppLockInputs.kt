package com.safeme.app.ui.screens.applock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeme.app.ui.theme.LocalAppColors
import kotlin.math.roundToInt

/**
 * Shake (wrong-code) feedback — mirrors the prototype's `.shake` keyframes
 * (translateX ±10px then ±6px, ~0.4s). Re-triggers whenever [trigger] flips
 * to true.
 */
@Composable
fun Modifier.shakeEffect(trigger: Boolean): Modifier {
    val offsetX = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger) {
            for (target in listOf(-10f, 10f, -6f, 6f, 0f)) {
                offsetX.animateTo(target, animationSpec = tween(80))
            }
        }
    }
    return this.offset { IntOffset(offsetX.value.roundToInt(), 0) }
}

/** Row of 6 dots; the first [filled] are brand-filled (prototype `.pindot`). */
@Composable
fun PinDots(
    filled: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.padding(vertical = 18.dp),
    ) {
        repeat(6) { index ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (index < filled) colors.brand else Color.Transparent
                    )
                    .border(
                        2.dp,
                        if (index < filled) colors.brand else colors.ink3,
                        CircleShape,
                    ),
            )
        }
    }
}

/** 3x3 keypad (prototype `.keypad`): 64dp circular keys, brand-soft bg. */
@Composable
fun PinKeypad(
    enabled: Boolean = true,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { digit ->
                    KeyCircle(
                        text = digit.toString(),
                        enabled = enabled,
                        onClick = { onDigit(digit) },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Invisible placeholder keeps the 3-column grid aligned.
            Box(Modifier.size(64.dp))
            KeyCircle(
                text = "0",
                enabled = enabled,
                onClick = { onDigit('0') },
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AlBackspaceIcon,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = colors.ink2,
                )
            }
        }
    }
}

@Composable
private fun KeyCircle(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg = if (pressed) colors.brand else colors.brandSoft
    val fg = if (pressed) Color.White else colors.ink
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}

/**
 * 3x3 pattern grid (prototype `.patgrid`). [selected] holds the 1-9 dot
 * indices in tap order; selected dots render brand-filled with their order
 * number. Requires at least 4 dots to be a valid credential.
 */
@Composable
fun PatternGrid(
    selected: List<Int>,
    enabled: Boolean = true,
    onDotTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val orderOf: (Int) -> Int = { index -> selected.indexOf(index).takeIf { it >= 0 }?.plus(1) ?: 0 }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.widthIn(max = 270.dp).fillMaxWidth(),
    ) {
        for (row in 0 until 3) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (col in 0 until 3) {
                    val index = row * 3 + col + 1
                    val order = orderOf(index)
                    val isOn = order > 0
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(if (isOn) colors.brand else if (pressed) colors.brandSoft else colors.brandSoft)
                            .clickable(
                                enabled = enabled,
                                interactionSource = interaction,
                                indication = null,
                                onClick = { onDotTap(index) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isOn) {
                            Text(
                                text = order.toString(),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Password text field (prototype `.field`): 48dp, lock icon, brand focus ring. */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(
                1.dp,
                if (focused) colors.brand else colors.line,
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp),
    ) {
        Icon(
            imageVector = AlLockIcon,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = colors.ink3,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 15.sp,
                    color = colors.ink3,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = colors.ink),
                interactionSource = interaction,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (focusRequester != null) Modifier.focusRequester(focusRequester)
                        else Modifier
                    ),
            )
        }
    }
}
