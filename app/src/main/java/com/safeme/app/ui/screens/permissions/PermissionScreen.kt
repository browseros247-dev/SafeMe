package com.safeme.app.ui.screens.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeme.app.R
import com.safeme.app.ui.components.blurredShadow
import com.safeme.app.ui.theme.LocalAppColors

@Composable
fun PermissionScreen(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    required: Boolean,
    step: Int,
    totalSteps: Int = 4,
    granted: Boolean,
    onBack: () -> Unit,
    grantLabel: String = stringResource(R.string.perm_grant),
    onGrant: () -> Unit,
    skipLabel: String? = null,
    onSkip: (() -> Unit)? = null
) {
    val colors = LocalAppColors.current
    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 108.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ChevronIcon,
                        contentDescription = stringResource(R.string.perm_back),
                        modifier = Modifier.size(20.dp),
                        tint = colors.ink
                    )
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(iconBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(30.dp),
                                tint = iconTint
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.ink,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = subtitle,
                            fontSize = 13.5.sp,
                            lineHeight = 20.25.sp,
                            color = colors.ink2,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(280.dp)
                        )

                        if (required) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.perm_required_badge),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.4.sp,
                                color = colors.brandDark,
                                modifier = Modifier
                                    .background(colors.brandSoft, RoundedCornerShape(99.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .then(
                                    if (granted) {
                                        Modifier
                                            .background(colors.brandSoft, CircleShape)
                                            .clickable(onClick = onGrant)
                                    } else {
                                        Modifier
                                            .blurredShadow(
                                                cornerRadius = 26.dp,
                                                color = colors.brand.copy(alpha = 0.35f),
                                                blurRadius = 20.dp,
                                                offsetY = 8.dp
                                            )
                                            .clip(CircleShape)
                                            .background(colors.brand)
                                            .clickable(onClick = onGrant)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (granted) stringResource(R.string.perm_granted) else grantLabel,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (granted) colors.brandDark else Color.White,
                                modifier = if (granted) Modifier.alpha(0.45f) else Modifier
                            )
                        }

                        if (skipLabel != null && onSkip != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onSkip),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = skipLabel,
                                    fontSize = 16.sp,
                                    color = colors.ink2
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.padding(top = 18.dp)) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..totalSteps) {
                        if (i > 1) Spacer(modifier = Modifier.width(6.dp))
                        if (i <= step) {
                            Box(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(7.dp)
                                    .background(colors.brand, RoundedCornerShape(50))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(colors.line, CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}
