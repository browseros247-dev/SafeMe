package com.safeme.app.ui.screens.focus

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

@Composable
fun FocusScreen(
    onStartFocus: () -> Unit = {},
    onManageWhitelist: () -> Unit = {},
    onAddWidget: () -> Unit = {},
    viewModel: FocusViewModel = viewModel(),
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
            SubHeader()
            HeroCard(
                presets = viewModel.presets,
                selectedPreset = state.selectedPreset,
                onSelectPreset = viewModel::selectPreset,
                onCustom = viewModel::customDuration,
                onStart = {
                    onStartFocus()
                    viewModel.startFocus()
                }
            )
            SectionTitle(text = stringResource(R.string.foc_sessions))
            SessionList(
                sessions = state.sessions,
                onToggle = viewModel::toggleSession
            )
            Spacer(Modifier.size(14.dp))
            RowCard(
                icon = FocGridIcon,
                title = stringResource(R.string.foc_whitelist_title),
                sub = stringResource(R.string.foc_whitelist_sub),
                action = stringResource(R.string.foc_manage),
                onAction = onManageWhitelist
            )
            Spacer(Modifier.size(14.dp))
            RowCard(
                icon = FocWidgetIcon,
                title = stringResource(R.string.foc_widget_title),
                sub = stringResource(R.string.foc_widget_sub),
                action = stringResource(R.string.foc_add),
                onAction = {
                    onAddWidget()
                    viewModel.addWidget()
                }
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
private fun SubHeader() {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp)
    ) {
        Text(
            text = stringResource(R.string.foc_title),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink,
            modifier = Modifier.weight(1f)
        )
        TextPill(
            text = stringResource(R.string.foc_pill),
            background = colors.brandSoft,
            contentColor = colors.brandDark
        )
    }
}

@Composable
private fun HeroCard(
    presets: List<FocusPreset>,
    selectedPreset: Int,
    onSelectPreset: (Int) -> Unit,
    onCustom: () -> Unit,
    onStart: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .cardShape()
            .background(
                Brush.linearGradient(
                    listOf(colors.brandMist, colors.surface),
                    start = androidx.compose.ui.geometry.Offset.Zero,
                    end = androidx.compose.ui.geometry.Offset.Infinite
                )
            )
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.foc_hero_label),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink2
        )
        Spacer(Modifier.size(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            presets.forEach { preset ->
                PresetButton(
                    minutes = preset.minutes,
                    selected = preset.minutes == selectedPreset,
                    onClick = { onSelectPreset(preset.minutes) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onCustom)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.foc_custom),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink2
            )
        }
        Spacer(Modifier.size(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = CircleShape,
                    ambientColor = colors.brand.copy(alpha = 0.35f),
                    spotColor = colors.brand.copy(alpha = 0.35f)
                )
                .clip(CircleShape)
                .background(colors.brand)
                .clickable(onClick = onStart),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = FocPlayIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.foc_start),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun PresetButton(
    minutes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val bg by animateColorAsState(
        targetValue = if (selected) colors.brandSoft else colors.surface,
        animationSpec = tween(150),
        label = "presetBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.brand else colors.line,
        animationSpec = tween(150),
        label = "presetBorder"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.brandDark else colors.ink,
        animationSpec = tween(150),
        label = "presetText"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "$minutes",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
        Text(
            text = stringResource(R.string.foc_min),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink3
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
private fun SessionList(
    sessions: List<FocusSession>,
    onToggle: (Int) -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardShape()
    ) {
        sessions.forEachIndexed { index, session ->
            if (index > 0) {
                HorizontalDivider(color = colors.line)
            }
            SessionRow(session = session, onToggle = { onToggle(session.id) })
        }
    }
}

@Composable
private fun SessionRow(session: FocusSession, onToggle: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 14.dp)
    ) {
        IconBox(
            icon = FocCalendarIcon,
            background = colors.iconAmberBg,
            tint = colors.warning,
            size = 40.dp,
            iconSize = 20.dp,
            radius = 13.dp
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = session.sub,
                fontSize = 12.sp,
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        MasterSwitch(checked = session.enabled, onToggle = onToggle)
    }
}

@Composable
private fun RowCard(
    icon: ImageVector,
    title: String,
    sub: String,
    action: String,
    onAction: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .cardShape()
            .padding(16.dp)
    ) {
        IconBox(
            icon = icon,
            background = colors.brandSoft,
            tint = colors.brandDark,
            size = 40.dp,
            iconSize = 20.dp,
            radius = 13.dp
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink
            )
            Text(
                text = sub,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = colors.ink2,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Box(
            modifier = Modifier
                .height(38.dp)
                .clip(CircleShape)
                .background(colors.brandSoft)
                .clickable(onClick = onAction)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = action,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.brandDark
            )
        }
    }
}

@Composable
private fun Note() {
    val colors = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = FocInfoIcon,
            contentDescription = null,
            tint = colors.brand,
            modifier = Modifier.size(16.dp).padding(top = 1.dp)
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.foc_note),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = colors.ink2
        )
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
