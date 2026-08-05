package com.safeme.app.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeme.app.ui.components.blurredShadow
import com.safeme.app.ui.theme.LocalAppColors

private val ShieldCheck: ImageVector = ImageVector.Builder(
    name = "ShieldCheck",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(12f, 3f)
        lineTo(19f, 6f)
        verticalLineToRelative(5f)
        curveToRelative(0f, 4.5f, -3f, 7.5f, -7f, 9f)
        curveToRelative(-4f, -1.5f, -7f, -4.5f, -7f, -9f)
        verticalLineTo(6f)
        close()
    }
    path(
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(9f, 12f)
        lineTo(11f, 14f)
        lineTo(15f, 10f)
    }
}.build()

@Composable
private fun Logo() {
    val colors = LocalAppColors.current
    val density = LocalDensity.current
    val gradient = remember(colors.brand, colors.brandDark, density) {
        with(density) {
            Brush.linearGradient(
                colors = listOf(colors.brandDark, colors.brand),
                start = Offset.Zero,
                end = Offset(68.dp.toPx(), 68.dp.toPx()),
            )
        }
    }
    Box(
        modifier = Modifier
            .size(68.dp)
            .blurredShadow(
                cornerRadius = 22.dp,
                color = colors.brand.copy(alpha = 0.42f),
                blurRadius = 32.dp,
                offsetY = 14.dp,
            )
            .clip(RoundedCornerShape(22.dp))
            .background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ShieldCheck,
            contentDescription = null,
            modifier = Modifier.size(36.dp),
            tint = Color.White,
        )
    }
}

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit = {}) {
    val typography = MaterialTheme.typography
    val colors = LocalAppColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 108.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Logo()

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.ink)) { append("Safe") }
                        withStyle(SpanStyle(color = colors.brand)) { append("Me") }
                    },
                    style = typography.displayLarge,
                    color = colors.ink,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = buildAnnotatedString {
                        append("A calm shield for your ")
                        withStyle(SpanStyle(color = colors.brand)) { append("attention") }
                        append(".")
                    },
                    style = typography.displayMedium,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "No ads. No trackers. No analytics.",
                    style = typography.bodyLarge,
                    color = colors.ink2,
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .blurredShadow(
                            cornerRadius = 26.dp,
                            color = colors.brand.copy(alpha = 0.35f),
                            blurRadius = 20.dp,
                            offsetY = 8.dp,
                        )
                        .clip(CircleShape)
                        .background(colors.brand)
                        .clickable(onClick = onGetStarted),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Get started",
                        style = typography.labelLarge,
                        color = Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(CircleShape)
                        .clickable { },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Review Terms",
                        style = typography.labelLarge,
                        color = colors.ink2,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.Center) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(7.dp)
                            .background(colors.brand, RoundedCornerShape(50)),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(colors.line, CircleShape),
                    )
                }
            }
        }
    }
}
