package com.safeme.app.ui.screens.blocking

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun BlockingScreen(
    onOpenBlockScreen: () -> Unit = {},
    onOpenVpn: () -> Unit = {},
    onOpenKeywords: () -> Unit = {},
    onOpenWebsites: () -> Unit = {},
    onOpenTitleBlock: () -> Unit = {},
    viewModel: BlockingViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalAppColors.current
    val comingSoonToast = stringResource(R.string.blk_coming_soon)
    val helpToast = stringResource(R.string.blk_help_toast)

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            SubHeader(
                title = stringResource(R.string.blk_title),
                onHelp = { viewModel.showToast(helpToast) }
            )
            ShieldCard(
                blocking = state.blocking,
                keywords = state.keywords,
                layersActive = state.layersActive,
                onToggle = viewModel::toggleBlocking
            )
            Spacer(Modifier.size(12.dp))
            StatsRow(
                blockedToday = state.blockedToday,
                keywords = state.keywords,
                layersActive = state.layersActive
            )
            Spacer(Modifier.size(12.dp))
            ManageCard(
                subtitle = state.manageSub,
                onKeywordsClick = onOpenKeywords,
                onWebsitesClick = onOpenWebsites
            )
            SectionTitle(text = stringResource(R.string.blk_more))
            MoreGrid(
                onOpenBlockScreen = onOpenBlockScreen,
                onVpn = onOpenVpn,
                onOpenTitleBlock = onOpenTitleBlock,
                onComingSoon = { viewModel.showToast(comingSoonToast) }
            )
            Spacer(Modifier.size(16.dp))
        }
        ToastHost(
            flow = viewModel.toasts,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun SubHeader(title: String, onHelp: () -> Unit) {
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
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onHelp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = BlkHelpIcon,
                contentDescription = null,
                tint = colors.ink2,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ShieldCard(
    blocking: Boolean,
    keywords: String,
    layersActive: String,
    onToggle: () -> Unit,
) {
    val colors = LocalAppColors.current
    val shieldSub = stringResource(R.string.blk_shield_sub, keywords, layersActive)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(colors.brandMist, colors.brandSoft)))
            .border(1.dp, colors.brand.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        ShieldRings()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(14.dp),
                        ambientColor = colors.brand.copy(alpha = 0.35f),
                        spotColor = colors.brand.copy(alpha = 0.35f)
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.brand),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = BlkShieldCheckIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.blk_shield_status),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.brandDark
                )
                Text(
                    text = shieldSub,
                    fontSize = 12.sp,
                    color = colors.brandDark.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.size(14.dp))
            MasterSwitch(checked = blocking, onToggle = onToggle)
        }
    }
}

@Composable
private fun BoxScope.ShieldRings() {
    val colors = LocalAppColors.current
    Box(modifier = Modifier.matchParentSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 34.dp, y = (-34).dp)
                .size(120.dp)
                .border(2.dp, colors.brand.copy(alpha = 0.16f), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 58.dp, y = 42.dp)
                .size(170.dp)
                .border(2.dp, colors.brand.copy(alpha = 0.16f), CircleShape)
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
private fun StatsRow(
    blockedToday: String,
    keywords: String,
    layersActive: String,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            value = blockedToday,
            label = stringResource(R.string.blk_stat_blocked_today),
            valueColor = colors.brand,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            value = keywords,
            label = stringResource(R.string.blk_stat_keywords),
            valueColor = colors.success,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            value = layersActive,
            label = stringResource(R.string.blk_stat_layers),
            valueColor = colors.warning,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
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
            color = valueColor
        )
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
            color = colors.ink2,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ManageCard(
    subtitle: String,
    onKeywordsClick: () -> Unit,
    onWebsitesClick: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(0) }
    val colors = LocalAppColors.current

    // Tapping anywhere on the card opens the screen of the currently selected
    // segment, so the whole surface is as tappable as the Keywords/Websites buttons.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardShape(radius = 18.dp)
            .clickable(onClick = {
                if (selectedTab == 0) onKeywordsClick() else onWebsitesClick()
            })
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(
                icon = BlkMenuIcon,
                background = colors.brandSoft,
                tint = colors.brandDark,
                size = 40.dp,
                iconSize = 20.dp,
                radius = 13.dp
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.blk_manage_title),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = colors.ink2,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                imageVector = BlkChevronRightIcon,
                contentDescription = null,
                tint = colors.ink3,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.size(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            SegButton(
                text = stringResource(R.string.blk_seg_keywords),
                selected = selectedTab == 0,
                onClick = {
                    selectedTab = 0
                    onKeywordsClick()
                },
                modifier = Modifier.weight(1f)
            )
            SegButton(
                text = stringResource(R.string.blk_seg_websites),
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    onWebsitesClick()
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SegButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) colors.brand else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else colors.ink2
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

private enum class IconVariant {
    Amber,
    Green,
    Red,
    Dark,
}

@Composable
private fun MoreGrid(
    onOpenBlockScreen: () -> Unit,
    onVpn: () -> Unit,
    onOpenTitleBlock: () -> Unit,
    onComingSoon: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoreCard(
                icon = BlkLayersIcon,
                variant = IconVariant.Amber,
                title = stringResource(R.string.blk_card_appfeature),
                sub = stringResource(R.string.blk_card_appfeature_sub),
                onClick = onComingSoon,
                modifier = Modifier.weight(1f)
            )
            MoreCard(
                icon = BlkSquarePlusIcon,
                variant = IconVariant.Dark,
                title = stringResource(R.string.blk_card_blockscreen),
                sub = stringResource(R.string.blk_card_blockscreen_sub),
                onClick = onOpenBlockScreen,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoreCard(
                icon = BlkShieldIcon,
                variant = IconVariant.Green,
                title = stringResource(R.string.blk_card_vpn),
                sub = stringResource(R.string.blk_card_vpn_sub),
                onClick = onVpn,
                modifier = Modifier.weight(1f)
            )
            MoreCard(
                icon = BlkShieldAlertIcon,
                variant = IconVariant.Red,
                title = stringResource(R.string.blk_card_antitamper),
                sub = stringResource(R.string.blk_card_antitamper_sub),
                onClick = onComingSoon,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoreCard(
                icon = BlkCrossIcon,
                variant = IconVariant.Amber,
                title = stringResource(R.string.blk_card_titleblock),
                sub = stringResource(R.string.blk_card_titleblock_sub),
                onClick = onOpenTitleBlock,
                modifier = Modifier.weight(1f)
            )
            // The SafeSearch card was removed; keep the last row balanced so the
            // remaining card doesn't stretch to full width.
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MoreCard(
    icon: ImageVector,
    variant: IconVariant,
    title: String,
    sub: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val (iconBg, iconTint) = when (variant) {
        IconVariant.Amber -> colors.iconAmberBg to colors.warning
        IconVariant.Green -> colors.iconGreenBg to colors.success
        IconVariant.Red -> colors.dangerBg to colors.danger
        IconVariant.Dark -> colors.iconDarkBg to colors.iconDarkFg
    }
    Column(
        modifier = modifier
            .cardShape(radius = 18.dp)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        IconBox(
            icon = icon,
            background = iconBg,
            tint = iconTint,
            size = 38.dp,
            iconSize = 19.dp,
            radius = 12.dp
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink
        )
        Text(
            text = sub,
            fontSize = 11.5.sp,
            color = colors.ink2,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 2.dp)
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
