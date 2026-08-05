package com.safeme.app.ui.screens.schedule

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeme.app.R
import com.safeme.app.ui.components.ToastHost
import com.safeme.app.ui.theme.LocalAppColors
import com.safeme.app.ui.theme.SerifFamily

private val HeroAccent = Color(0xFFE8A07E)
private val HeroPillDot = Color(0xFF7CE0B3)

@Composable
fun ScheduleScreen(
    onNewSchedule: () -> Unit = {},
    onEditSchedule: () -> Unit = {},
    viewModel: ScheduleViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalAppColors.current

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            SubHeader(
                title = stringResource(R.string.sch_title),
                onNew = onNewSchedule
            )
            HeroCard(
                count = state.heroCount,
                nextBoundary = state.nextBoundary
            )
            SectionTitle(text = stringResource(R.string.sch_your_schedules))
            ScheduleList(
                cards = state.cards,
                onToggle = viewModel::toggleSchedule,
                onEdit = onEditSchedule
            )
            Spacer(Modifier.size(12.dp))
            Note()
            Spacer(Modifier.size(16.dp))
        }
        ToastHost(
            flow = viewModel.toasts,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun SubHeader(title: String, onNew: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp)
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .height(38.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = colors.brand.copy(alpha = 0.35f),
                    spotColor = colors.brand.copy(alpha = 0.35f)
                )
                .clip(CircleShape)
                .background(colors.brand)
                .clickable(onClick = onNew)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.sch_new),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun HeroCard(count: String, nextBoundary: String) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(colors.brandDark, colors.brand, HeroAccent),
                    start = androidx.compose.ui.geometry.Offset.Zero,
                    end = androidx.compose.ui.geometry.Offset.Infinite
                )
            )
            .padding(22.dp)
    ) {
        HeroRings()
        Column {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = SchCalendarIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.size(14.dp))
            Text(
                text = stringResource(R.string.sch_hero_tag),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                text = count,
                fontFamily = SerifFamily,
                fontSize = 27.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
            )
            Text(
                text = nextBoundary,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.92f)
            )
            Spacer(Modifier.size(14.dp))
            HeroPill(
                text = stringResource(R.string.sch_hero_pill),
                dotColor = HeroPillDot
            )
        }
    }
}

@Composable
private fun HeroRings() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = (-30).dp)
                .size(150.dp)
                .border(2.dp, Color.White.copy(alpha = 0.18f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 58.dp, y = 42.dp)
                .size(170.dp)
                .border(2.dp, Color.White.copy(alpha = 0.18f), CircleShape)
        )
    }
}

@Composable
private fun HeroPill(text: String, dotColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun ScheduleList(
    cards: List<ScheduleCard>,
    onToggle: (Int) -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardShape(radius = 20.dp)
    ) {
        val colors = LocalAppColors.current
        cards.forEachIndexed { index, card ->
            if (index > 0) {
                HorizontalDivider(color = colors.line)
            }
            ScheduleCard(
                card = card,
                onToggle = { onToggle(card.id) },
                onEdit = onEdit
            )
        }
    }
}

@Composable
private fun ScheduleCard(
    card: ScheduleCard,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(
                icon = SchCalendarIcon,
                background = colors.iconAmberBg,
                tint = colors.warning,
                size = 40.dp,
                iconSize = 20.dp,
                radius = 13.dp
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = card.days,
                    fontSize = 12.sp,
                    color = colors.ink2,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            MasterSwitch(checked = card.enabled, onToggle = onToggle)
        }
        Spacer(Modifier.size(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = SchClockIcon,
                contentDescription = null,
                tint = colors.brand,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = card.time,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colors.brandDark
            )
        }
        Spacer(Modifier.size(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextPill(text = card.mode, background = colors.successBg, contentColor = colors.success)
            TextPill(text = card.apps, background = colors.iconAmberBg, contentColor = colors.warning)
        }
        Spacer(Modifier.size(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = card.next,
                fontSize = 12.sp,
                color = colors.ink2,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.sch_edit),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink2
                )
            }
        }
    }
}

@Composable
private fun TextPill(text: String, background: Color, contentColor: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
private fun MasterSwitch(checked: Boolean, onToggle: () -> Unit) {
    val colors = LocalAppColors.current
    val bg by animateColorAsState(
        targetValue = if (checked) colors.brand else colors.swOff,
        animationSpec = tween(200),
        label = "switchBg"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 21.dp else 0.dp,
        animationSpec = tween(200),
        label = "switchThumb"
    )
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 31.dp)
            .clip(CircleShape)
            .background(bg)
            .semantics { role = Role.Switch }
            .clickable(role = Role.Switch, onClick = onToggle),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = 3.dp + thumbOffset)
                .size(25.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    val colors = LocalAppColors.current
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.2.sp,
        color = colors.ink,
        modifier = Modifier.padding(start = 2.dp, top = 20.dp, bottom = 10.dp)
    )
}

@Composable
private fun Note() {
    val colors = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = SchInfoIcon,
            contentDescription = null,
            tint = colors.brand,
            modifier = Modifier.size(16.dp).padding(top = 1.dp)
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.sch_note),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = colors.ink2
        )
    }
}

@Composable
private fun IconBox(
    icon: ImageVector,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 19.dp,
    radius: Dp = 12.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun Modifier.cardShape(radius: Dp = 20.dp): Modifier {
    val colors = LocalAppColors.current
    return this
        .shadow(
            elevation = 1.dp,
            shape = RoundedCornerShape(radius),
            ambientColor = colors.ink.copy(alpha = 0.02f),
            spotColor = colors.ink.copy(alpha = 0.02f)
        )
        .clip(RoundedCornerShape(radius))
        .background(colors.surface)
        .border(1.dp, colors.line, RoundedCornerShape(radius))
}
