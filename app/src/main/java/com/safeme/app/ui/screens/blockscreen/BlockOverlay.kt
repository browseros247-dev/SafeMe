package com.safeme.app.ui.screens.blockscreen

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeme.app.R
import com.safeme.app.ui.components.blurredShadow
import com.safeme.app.ui.theme.LocalAppColors
import com.safeme.app.ui.theme.SerifFamily
import kotlinx.coroutines.delay

@Composable
fun BlockOverlay(
    dwell: Int,
    msg: String,
    whyOn: Boolean,
    onClose: () -> Unit,
    whyReason: String? = null,
) {
    var remaining by remember { mutableIntStateOf(dwell) }
    var ready by remember { mutableStateOf(dwell <= 0) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val colors = LocalAppColors.current
    val logoBrush = remember(density, colors.brandDark, colors.brand) {
        with(density) {
            Brush.linearGradient(
                colors = listOf(colors.brandDark, colors.brand),
                start = Offset.Zero,
                end = Offset(84.dp.toPx(), 84.dp.toPx())
            )
        }
    }

    LaunchedEffect(dwell) {
        remaining = dwell
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
        ready = true
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.cardDark1)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 30.dp, end = 30.dp, top = 52.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .blurredShadow(
                            cornerRadius = 24.dp,
                            color = colors.brand.copy(alpha = 0.4f),
                            blurRadius = 40.dp,
                            offsetY = 14.dp
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .background(logoBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ShieldCheckIcon,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.bs_site_blocked),
                    fontFamily = SerifFamily,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = msg,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = colors.previewMsg,
                    textAlign = TextAlign.Center
                )
                if (whyOn) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .background(colors.brand.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                            .clickable {
                                Toast.makeText(
                                    context,
                                    whyReason ?: context.getString(R.string.bs_toast_why),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.bs_overlay_why),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.iconDarkFg
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val radius = 52.dp.toPx()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.12f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 9.dp.toPx())
                        )
                        val sweep = if (dwell > 0) {
                            (remaining.toFloat() / dwell.toFloat()) * 360f
                        } else 0f
                        drawArc(
                            color = colors.brand,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = Size(radius * 2f, radius * 2f),
                            style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = "$remaining",
                        style = TextStyle(
                            fontSize = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontFeatureSettings = "tnum"
                        )
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.bs_overlay_label),
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.previewLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(CircleShape)
                        .then(
                            if (ready) {
                                Modifier.background(colors.brand).clickable(onClick = onClose)
                            } else {
                                Modifier.background(colors.cardDark2)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.bs_close),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (ready) Color.White else colors.iconDarkFg.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}
