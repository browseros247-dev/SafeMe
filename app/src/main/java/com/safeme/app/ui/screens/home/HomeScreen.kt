package com.safeme.app.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeme.app.R
import com.safeme.app.ui.components.ToastHost
import com.safeme.app.ui.theme.LocalAppColors
import com.safeme.app.ui.theme.SerifFamily

private val A11yBannerBg = Color(0x12F44336)
private val HeroAccent = Color(0xFFE8A07E)
private val HeroRingTrack = Color(0xFFEADFD6)

@Composable
fun HomeScreen(
    onReviewShield: () -> Unit = {},
    onStartFocus: () -> Unit = {},
    onAddKeyword: () -> Unit = {},
    onNewSchedule: () -> Unit = {},
    onBackup: () -> Unit = {},
    onHistory: () -> Unit = {},
    onOpenAccessibility: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LocalAppColors.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            HomeHeaderRow(greeting = state.greeting, dateLine = state.dateLine)
            Spacer(Modifier.height(16.dp))
            HeroCard(onReviewShield = onReviewShield)
            if (!state.a11yEnabled) {
                Spacer(Modifier.height(12.dp))
                A11yBanner(onOpenAccessibility = onOpenAccessibility)
            }
            Spacer(Modifier.height(14.dp))
            MasterProtectionCard(
                enabled = state.masterProtection,
                onToggle = viewModel::toggleMasterProtection
            )
            val allActionsToast = stringResource(R.string.home_toast_all)
            SectionTitle(
                title = stringResource(R.string.home_quick_actions),
                moreText = stringResource(R.string.home_all),
                onMore = { viewModel.showToast(allActionsToast) }
            )
            QuickActionsGrid(
                onStartFocus = onStartFocus,
                onAddKeyword = onAddKeyword,
                onNewSchedule = onNewSchedule,
                onBackup = onBackup
            )
            SectionTitle(title = stringResource(R.string.home_today))
            StatsRow(
                blocked = state.blockedToday,
                focusTime = state.focusTime,
                schedules = state.scheduleCount
            )
            SectionTitle(
                title = stringResource(R.string.home_recent_activity),
                moreText = stringResource(R.string.home_history),
                onMore = onHistory
            )
            FeedCard()
            Spacer(Modifier.height(16.dp))
        }
        ToastHost(
            flow = viewModel.toasts,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun HomeHeaderRow(greeting: String, dateLine: String) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(colors.brand, HeroAccent),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.home_user_name).take(1),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_header_greeting, greeting, stringResource(R.string.home_user_name)),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = dateLine,
                fontSize = 12.sp,
                color = colors.ink2
            )
        }
        Spacer(Modifier.width(10.dp))
        Pill(
            text = stringResource(R.string.home_protected),
            dotColor = colors.success,
            bg = colors.successBg,
            contentColor = colors.success
        )
    }
}

@Composable
private fun HeroCard(onReviewShield: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .cardShape(radius = 20.dp)
            .background(
                Brush.linearGradient(
                    0f to colors.brandDark,
                    0.6f to colors.brand,
                    1f to HeroAccent,
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
            )
            .padding(22.dp)
    ) {
        HeroRings()
        Column {
            Text(
                text = stringResource(R.string.home_shield_tag),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.home_hero_title),
                fontFamily = SerifFamily,
                fontSize = 27.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_hero_subtitle),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.92f)
            )
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HeroRing(progress = 0.8f)
                Box(
                    modifier = Modifier
                        .height(52.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .clickable(onClick = onReviewShield)
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.home_review_shield),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroRings() {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(150.dp)
                .offset(x = 30.dp, y = (-30).dp)
                .border(2.dp, Color.White.copy(alpha = 0.18f), CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(170.dp)
                .offset(x = 58.dp, y = 42.dp)
                .border(2.dp, Color.White.copy(alpha = 0.18f), CircleShape)
        )
    }
}

@Composable
private fun HeroRing(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(56.dp)) {
        val stroke = 6.dp.toPx() * (56f / 64f)
        val r = 26.dp.toPx() * (56f / 64f)
        val topLeft = Offset(
            size.width / 2f - r,
            size.height / 2f - r
        )
        val arcSize = Size(r * 2f, r * 2f)
        drawArc(
            color = HeroRingTrack,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke)
        )
        drawArc(
            color = Color.White,
            startAngle = -90f,
            sweepAngle = 360f * progress,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun A11yBanner(onOpenAccessibility: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardShape(radius = 20.dp)
            .background(A11yBannerBg)
            .border(1.dp, colors.danger, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.dangerBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = HomeAccessibilityIcon,
                contentDescription = null,
                tint = colors.danger,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_a11y_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_a11y_sub),
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = colors.ink2
            )
        }
        Spacer(Modifier.width(8.dp))
        Pill(
            text = stringResource(R.string.home_disabled),
            dotColor = colors.danger,
            bg = colors.dangerBg,
            contentColor = colors.danger
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .height(38.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(colors.brandSoft)
                .clickable(onClick = onOpenAccessibility)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.home_open),
                color = colors.brandDark,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MasterProtectionCard(enabled: Boolean, onToggle: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .cardShape()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_master_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.home_master_sub),
                fontSize = 12.5.sp,
                color = colors.ink2
            )
        }
        Spacer(Modifier.width(14.dp))
        MasterSwitch(enabled = enabled, onToggle = onToggle)
    }
}

@Composable
private fun MasterSwitch(enabled: Boolean, onToggle: () -> Unit) {
    val colors = LocalAppColors.current
    val bg by animateColorAsState(if (enabled) colors.brand else colors.swOff, tween(200))
    val thumbOffset by animateDpAsState(if (enabled) 21.dp else 0.dp, tween(200))
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 31.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .semantics { role = Role.Switch }
            .clickable(role = Role.Switch, onClick = onToggle)
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .size(25.dp)
                .offset(x = thumbOffset)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun SectionTitle(title: String, moreText: String? = null, onMore: () -> Unit = {}) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.2.sp,
            color = colors.ink
        )
        if (moreText != null) {
            Spacer(Modifier.weight(1f))
            Text(
                text = moreText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.brandDark,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onMore)
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun QuickActionsGrid(
    onStartFocus: () -> Unit,
    onAddKeyword: () -> Unit,
    onNewSchedule: () -> Unit,
    onBackup: () -> Unit
) {
    Column {
        Row {
            QuickActionCard(
                icon = HomeClockIcon,
                title = stringResource(R.string.home_start_focus),
                sub = stringResource(R.string.home_start_focus_sub),
                onClick = onStartFocus,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            QuickActionCard(
                icon = HomeHashtagIcon,
                title = stringResource(R.string.home_add_keyword),
                sub = stringResource(R.string.home_add_keyword_sub),
                onClick = onAddKeyword,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row {
            QuickActionCard(
                icon = HomeCalendarIcon,
                title = stringResource(R.string.home_new_schedule),
                sub = stringResource(R.string.home_new_schedule_sub),
                onClick = onNewSchedule,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            QuickActionCard(
                icon = HomeDownloadIcon,
                title = stringResource(R.string.home_backup),
                sub = stringResource(R.string.home_backup_sub),
                onClick = onBackup,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    sub: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .shadow(
                1.dp,
                RoundedCornerShape(18.dp),
                ambientColor = colors.ink.copy(alpha = 0.02f),
                spotColor = colors.ink.copy(alpha = 0.02f)
            )
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.brandSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.brandDark,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = sub,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            color = colors.ink2
        )
    }
}

@Composable
private fun StatsRow(blocked: String, focusTime: String, schedules: String) {
    val colors = LocalAppColors.current
    Row {
        StatCard(value = blocked, label = stringResource(R.string.home_stat_blocked), color = colors.brand, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        StatCard(value = focusTime, label = stringResource(R.string.home_stat_focus_time), color = colors.success, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        StatCard(value = schedules, label = stringResource(R.string.home_stat_schedules), color = colors.warning, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
            color = colors.ink2
        )
    }
}

@Composable
private fun FeedCard() {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardShape()
            .padding(horizontal = 22.dp)
    ) {
        FeedRow(
            dotColor = colors.brand,
            title = stringResource(R.string.home_feed_1_t),
            sub = stringResource(R.string.home_feed_1_s),
            time = stringResource(R.string.home_feed_1_tm)
        )
        HorizontalDivider(color = colors.line)
        FeedRow(
            dotColor = colors.success,
            title = stringResource(R.string.home_feed_2_t),
            sub = stringResource(R.string.home_feed_2_s),
            time = stringResource(R.string.home_feed_2_tm)
        )
        HorizontalDivider(color = colors.line)
        FeedRow(
            dotColor = colors.warning,
            title = stringResource(R.string.home_feed_3_t),
            sub = stringResource(R.string.home_feed_3_s),
            time = stringResource(R.string.home_feed_3_tm)
        )
    }
}

@Composable
private fun FeedRow(dotColor: Color, title: String, sub: String, time: String) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = sub,
                fontSize = 12.sp,
                color = colors.ink2
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = time,
                fontSize = 11.sp,
                color = colors.ink3
            )
        }
    }
}

@Composable
private fun Pill(text: String, dotColor: Color, bg: Color, contentColor: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
private fun Modifier.cardShape(radius: Dp = 20.dp): Modifier {
    val colors = LocalAppColors.current
    return this
        .shadow(
            1.dp,
            RoundedCornerShape(radius),
            ambientColor = colors.ink.copy(alpha = 0.02f),
            spotColor = colors.ink.copy(alpha = 0.02f)
        )
        .clip(RoundedCornerShape(radius))
        .background(colors.surface)
        .border(1.dp, colors.line, RoundedCornerShape(radius))
}
