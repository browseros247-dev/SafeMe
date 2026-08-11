package com.safeme.app.ui.screens.profile

import android.content.Context
import android.os.PowerManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeme.app.R
import com.safeme.app.data.ThemePref
import com.safeme.app.ui.components.ToastHost
import com.safeme.app.ui.screens.permissions.hasNotificationsPermission
import com.safeme.app.ui.util.isAccessibilityEnabled
import com.safeme.app.ui.theme.LocalAppColors

@Composable
fun ProfileScreen(
    onOpen: (String) -> Unit = {},
    viewModel: ProfileViewModel = viewModel(),
) {
    val colors = LocalAppColors.current
    val state by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            SubHeader(onShare = viewModel::share)
            IdentityCard()
            GroupLabel(text = stringResource(R.string.prof_appearance))
            ThemeCard(
                themePref = state.themePref,
                onSelectTheme = viewModel::selectTheme
            )
            GroupLabel(text = stringResource(R.string.prof_protection))
            QaGrid(
                onOpen = onOpen,
                onToast = viewModel::comingSoon
            )
            TroubleshootCard(onOpen = { onOpen("troubleshoot") })
            GroupLabel(text = stringResource(R.string.prof_support))
            SupportList(
                onOpen = onOpen,
                onContact = viewModel::contact
            )
            GroupLabel(
                text = stringResource(R.string.prof_danger),
                color = colors.danger
            )
            DeleteButton(onClick = { showDeleteDialog = true })
            Footer()
            Spacer(Modifier.size(16.dp))
        }
        ToastHost(
            flow = viewModel.toasts,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        )
    }

    if (showDeleteDialog) {
        DeleteDialog(
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteAccount()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

@Composable
private fun SubHeader(onShare: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.prof_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.6).sp,
                color = colors.ink,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.brandSoft)
                    .clickable(onClick = onShare),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ProfShareIcon,
                    contentDescription = stringResource(R.string.prof_share),
                    tint = colors.ink2,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.prof_header_sub),
            fontSize = 12.sp,
            lineHeight = 20.sp,
            color = colors.ink2
        )
    }
}

@Composable
private fun IdentityCard() {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .cardShape()
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(colors.brand, colors.brandDark))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "A",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.prof_name),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink
            )
            Text(
                text = stringResource(R.string.prof_since),
                fontSize = 12.sp,
                color = colors.ink2,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        TextPill(
            text = stringResource(R.string.prof_streak),
            background = colors.successBg,
            contentColor = colors.success
        )
    }
}

@Composable
private fun GroupLabel(text: String, color: Color = LocalAppColors.current.ink3) {
    Text(
        text = text.uppercase(),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.6.sp,
        color = color,
        modifier = Modifier.padding(start = 2.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun ThemeCard(
    themePref: ThemePref,
    onSelectTheme: (ThemePref) -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardShape()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBox(
                icon = ProfMoonIcon,
                background = colors.brandSoft,
                tint = colors.brandDark,
                size = 40.dp,
                iconSize = 20.dp,
                radius = 13.dp
            )
            Column {
                Text(
                    text = stringResource(R.string.prof_theme_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink
                )
                Text(
                    text = stringResource(R.string.prof_theme_sub),
                    fontSize = 12.sp,
                    color = colors.ink2,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
        Spacer(Modifier.size(14.dp))
        SegmentedControl(
            themePref = themePref,
            onSelectTheme = onSelectTheme
        )
    }
}

@Composable
private fun SegmentedControl(
    themePref: ThemePref,
    onSelectTheme: (ThemePref) -> Unit,
) {
    val colors = LocalAppColors.current
    val options = listOf(
        ThemePref.SYSTEM to R.string.prof_theme_system,
        ThemePref.DARK to R.string.prof_theme_dark,
        ThemePref.LIGHT to R.string.prof_theme_light,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .padding(3.dp)
    ) {
        options.forEach { (pref, labelRes) ->
            val selected = pref == themePref
            val bg by animateColorAsState(
                targetValue = if (selected) colors.brand else Color.Transparent,
                animationSpec = tween(150),
                label = "segBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) Color.White else colors.ink2,
                animationSpec = tween(150),
                label = "segText"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(bg)
                    .clickable { onSelectTheme(pref) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(labelRes),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun QaGrid(
    onOpen: (String) -> Unit,
    onToast: () -> Unit,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    // Live permission status: notifications + battery + accessibility.
    val grantedCount = run {
        var count = 0
        if (hasNotificationsPermission(context)) count++
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(context.packageName)) count++
        if (isAccessibilityEnabled(context)) count++
        count
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            // Match the prototype's grid: cards in a row stretch to equal height.
            modifier = Modifier.height(IntrinsicSize.Min)
        ) {
            QaCard(
                icon = ProfLockIcon,
                background = colors.iconAmberBg,
                tint = colors.warning,
                title = stringResource(R.string.prof_applock_title),
                sub = stringResource(R.string.prof_applock_sub),
                onClick = { onOpen("applock") },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            QaCard(
                icon = ProfPersonIcon,
                background = colors.iconAmberBg,
                tint = colors.warning,
                title = stringResource(R.string.prof_acc_title),
                sub = stringResource(R.string.prof_acc_sub),
                onClick = onToast,
                badge = stringResource(R.string.prof_acc_soon),
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            // Match the prototype's grid: cards in a row stretch to equal height.
            modifier = Modifier.height(IntrinsicSize.Min)
        ) {
            QaCard(
                icon = ProfDownloadIcon,
                background = colors.iconGreenBg,
                tint = colors.success,
                title = stringResource(R.string.prof_backup_title),
                sub = stringResource(R.string.prof_backup_sub),
                onClick = { onOpen("backup") },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            QaCard(
                icon = ProfShieldCheckIcon,
                background = colors.iconGreenBg,
                tint = colors.success,
                title = stringResource(R.string.prof_permissions_title),
                sub = stringResource(R.string.prof_permissions_sub, grantedCount),
                onClick = { onOpen("permissions") },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun QaCard(
    icon: ImageVector,
    background: Color,
    tint: Color,
    title: String,
    sub: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            IconBox(
                icon = icon,
                background = background,
                tint = tint,
                size = 40.dp,
                iconSize = 19.dp,
                radius = 13.dp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Text(
                text = title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sub,
                fontSize = 11.5.sp,
                lineHeight = 15.5.sp,
                color = colors.ink2,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .clip(CircleShape)
                    .background(colors.ink3)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun TroubleshootCard(onOpen: () -> Unit) {
    val colors = LocalAppColors.current
    RowCard(
        icon = ProfPlayCircleIcon,
        background = colors.iconAmberBg,
        tint = colors.warning,
        title = stringResource(R.string.prof_troubleshoot_title),
        sub = stringResource(R.string.prof_troubleshoot_sub),
        onClick = onOpen,
        modifier = Modifier.padding(top = 14.dp)
    )
}

@Composable
private fun RowCard(
    icon: ImageVector,
    background: Color,
    tint: Color,
    title: String,
    sub: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .cardShape()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        IconBox(
            icon = icon,
            background = background,
            tint = tint,
            size = 40.dp,
            iconSize = 20.dp,
            radius = 13.dp
        )
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
        Icon(
            imageVector = ProfChevIcon,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SupportList(
    onOpen: (String) -> Unit,
    onContact: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .cardShape()
    ) {
        ListRow(
            icon = ProfCrashIcon,
            background = colors.iconRedBg,
            tint = colors.danger,
            title = stringResource(R.string.prof_crash_title),
            sub = stringResource(R.string.prof_crash_sub),
            badge = stringResource(R.string.prof_crash_badge),
            badgeBg = colors.danger,
            onClick = { onOpen("crash") }
        )
        HorizontalDivider(color = colors.line)
        ListRow(
            icon = ProfAntennaIcon,
            background = colors.iconDarkBg,
            tint = colors.iconDarkFg,
            title = stringResource(R.string.prof_relay_title),
            sub = stringResource(R.string.prof_relay_sub),
            onClick = { onOpen("relay") }
        )
        HorizontalDivider(color = colors.line)
        ListRow(
            icon = ProfInfoIcon,
            background = colors.brandSoft,
            tint = colors.brandDark,
            title = stringResource(R.string.prof_about_title),
            sub = stringResource(R.string.prof_about_sub),
            onClick = { onOpen("about") }
        )
        HorizontalDivider(color = colors.line)
        ListRow(
            icon = ProfChatIcon,
            background = colors.iconAmberBg,
            tint = colors.warning,
            title = stringResource(R.string.prof_contact_title),
            sub = stringResource(R.string.prof_contact_sub),
            onClick = onContact
        )
    }
}

@Composable
private fun ListRow(
    icon: ImageVector,
    background: Color,
    tint: Color,
    title: String,
    sub: String,
    onClick: () -> Unit,
    badge: String? = null,
    badgeBg: Color = LocalAppColors.current.danger,
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        IconBox(
            icon = icon,
            background = background,
            tint = tint,
            size = 40.dp,
            iconSize = 20.dp,
            radius = 13.dp
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sub,
                fontSize = 12.sp,
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(badgeBg)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = ProfChevIcon,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DeleteButton(onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = colors.danger.copy(alpha = 0.35f),
                spotColor = colors.danger.copy(alpha = 0.35f)
            )
            .clip(CircleShape)
            .background(colors.danger)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.prof_delete),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun DeleteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.prof_delete_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.danger
            )
        },
        text = {
            Text(
                text = stringResource(R.string.prof_delete_body),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colors.ink2
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.prof_delete_confirm),
                    color = colors.danger,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.prof_delete_cancel),
                    color = colors.ink2,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun Footer() {
    val colors = LocalAppColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.prof_footer_1),
            fontSize = 11.5.sp,
            lineHeight = 18.4.sp,
            color = colors.ink3
        )
        Text(
            text = stringResource(R.string.prof_footer_2),
            fontSize = 11.5.sp,
            lineHeight = 18.4.sp,
            color = colors.ink3
        )
    }
}

@Composable
private fun TextPill(text: String, background: Color, contentColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Box(
            modifier = Modifier.size(7.dp).clip(CircleShape).background(contentColor)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

@Composable
internal fun IconBox(
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
internal fun Modifier.cardShape(radius: Dp = 20.dp): Modifier {
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
}
