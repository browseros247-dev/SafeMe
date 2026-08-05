package com.safeme.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeme.app.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

private data class HostToast(
    val id: Int,
    val text: String,
    val leaving: Boolean = false
)

/**
 * Holds a transient in-app toast (dark pill, prototype sc-home `.toast`).
 * Each message on [flow] is shown for ~2.1s then fades out.
 */
@Composable
fun ToastHost(
    flow: Flow<String>,
    modifier: Modifier = Modifier
) {
    var counter by remember { mutableIntStateOf(0) }
    var toasts by remember { mutableStateOf(listOf<HostToast>()) }

    LaunchedEffect(flow) {
        flow.collect { message ->
            val id = counter++
            toasts = toasts + HostToast(id, message)
            delay(2100)
            toasts = toasts.map { if (it.id == id) it.copy(leaving = true) else it }
            delay(320)
            toasts = toasts.filterNot { it.id == id }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        toasts.forEach { toast ->
            AnimatedVisibility(
                visible = !toast.leaving,
                enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 2 },
                exit = fadeOut(tween(300))
            ) {
                ToastPill(toast.text)
            }
        }
    }
}

@Composable
private fun ToastPill(text: String) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(999.dp),
                ambientColor = colors.ink.copy(alpha = 0.18f),
                spotColor = colors.ink.copy(alpha = 0.18f)
            )
            .clip(RoundedCornerShape(999.dp))
            .background(colors.toastBg)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            color = colors.toastFg,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
