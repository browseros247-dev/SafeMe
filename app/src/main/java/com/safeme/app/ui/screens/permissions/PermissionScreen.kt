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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeme.app.R
import com.safeme.app.ui.components.blurredShadow
import com.safeme.app.ui.theme.LocalAppColors

/**
 * Prototype-styled permission step ("Typeless" layout structure):
 * open editorial composition — no card. Two variants:
 *  - simple: centered hero title + sub, bottom-anchored CTA block;
 *  - timeline ([timeline] != null): inline back+title header, description,
 *    numbered vertical how-to timeline, note line, bottom CTA block.
 */
@Composable
fun PermissionScreen(
    title: String,
    subtitle: String,
    required: Boolean,
    step: Int,
    totalSteps: Int = 3,
    granted: Boolean,
    onBack: () -> Unit,
    grantLabel: String = stringResource(R.string.perm_grant),
    onGrant: () -> Unit,
    skipLabel: String? = null,
    onSkip: (() -> Unit)? = null,
    accentWord: String? = null,
    timeline: List<String>? = null,
    note: String? = null,
) {
    val colors = LocalAppColors.current
    val isTimeline = timeline != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 108.dp)
    ) {
        if (!isTimeline) {
            // Small floating back control, top-left (prototype keeps forward-
            // only steps, SafeMe needs a way out of re-entered flows).
            Box(modifier = Modifier.padding(top = 12.dp)) {
                BackChip(onBack = onBack)
            }
        }

        if (isTimeline) {
            TimelineHeader(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                modifier = Modifier.padding(top = 20.dp)
            )
            Spacer(modifier = Modifier.height(28.dp))
            TimelineSteps(steps = timeline)
            if (note != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = note,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = colors.ink3
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (required) {
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
                    Spacer(modifier = Modifier.height(14.dp))
                }
                HeroTitle(
                    title = title,
                    accentWord = accentWord,
                    color = colors.ink,
                    accentColor = colors.brand,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.5.sp,
                    lineHeight = 20.25.sp,
                    color = colors.ink2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 300.dp)
                )
            }
        }

        if (isTimeline) {
            Spacer(modifier = Modifier.weight(1f))
        } else {
            Spacer(modifier = Modifier.height(20.dp))
        }

        GrantPill(
            granted = granted,
            label = if (granted) stringResource(R.string.perm_granted) else grantLabel,
            onGrant = onGrant
        )

        // [Welcome-parity CTA stack] Fixed-height secondary slot (ghost Skip
        // when optional, empty otherwise) keeps the grant pill at the same
        // vertical position on every step — matched to the welcome screen's
        // "Get started" placement (pill → 8dp → 40dp slot → 12dp → dots).
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            contentAlignment = Alignment.Center
        ) {
            if (skipLabel != null && onSkip != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(CircleShape)
                        .border(1.2.dp, colors.line, CircleShape)
                        .clickable(onClick = onSkip),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = skipLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink2
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ProgressDots(step = step, totalSteps = totalSteps)
    }
}

/** Prototype `.sub()` header: chevron + inline title + muted desc below. */
@Composable
private fun TimelineHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackChip(onBack = onBack)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.ink
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            fontSize = 13.5.sp,
            lineHeight = 21.sp,
            color = colors.ink2
        )
    }
}

@Composable
private fun BackChip(onBack: () -> Unit) {
    val colors = LocalAppColors.current
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

/** Hero headline; [accentWord] renders in the brand color like `em` in the prototype. */
@Composable
private fun HeroTitle(
    title: String,
    accentWord: String?,
    color: Color,
    accentColor: Color,
    textAlign: TextAlign = TextAlign.Start,
) {
    val styled: AnnotatedString = if (accentWord != null && title.contains(accentWord)) {
        buildAnnotatedString {
            val idx = title.indexOf(accentWord)
            append(title.take(idx))
            pushStyle(SpanStyle(color = accentColor))
            append(accentWord)
            pop()
            append(title.drop(idx + accentWord.length))
        }
    } else {
        AnnotatedString(title)
    }
    Text(
        text = styled,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.3).sp,
        color = color,
        textAlign = textAlign
    )
}

/**
 * Numbered vertical how-to timeline (prototype `.a11y-tl`): 30dp brandSoft
 * circles with bold serif-style numerals connected by a hairline.
 */
@Composable
private fun TimelineSteps(steps: List<String>) {
    val colors = LocalAppColors.current
    Column {
        steps.forEachIndexed { index, stepText ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colors.brandSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (index + 1).toString(),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.brandDark
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stepText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = colors.ink,
                    modifier = Modifier.weight(1f)
                )
            }
            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .width(1.5.dp)
                        .height(18.dp)
                        .background(colors.line)
                )
                Spacer(modifier = Modifier.height(0.dp))
            }
        }
    }
}

@Composable
private fun GrantPill(granted: Boolean, label: String, onGrant: () -> Unit) {
    val colors = LocalAppColors.current
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
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (granted) colors.brandDark else Color.White,
            modifier = if (granted) Modifier.alpha(0.45f) else Modifier
        )
    }
}

@Composable
private fun ProgressDots(step: Int, totalSteps: Int) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
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
