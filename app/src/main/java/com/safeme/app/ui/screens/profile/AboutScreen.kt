package com.safeme.app.ui.screens.profile

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeme.app.R
import com.safeme.app.ui.components.ToastHost
import com.safeme.app.ui.screens.permissions.ChevronIcon
import com.safeme.app.ui.theme.LocalAppColors
import com.safeme.app.ui.theme.SerifFamily

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: ProfileViewModel = viewModel(),
) {
    val colors = LocalAppColors.current
    val websiteToast = stringResource(R.string.prof_about_website_toast)
    val privacyToast = stringResource(R.string.prof_about_privacy_toast)

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            AboutHeader(onBack = onBack)
            AboutIdentityCard()
            AboutLinksList(
                onWebsite = { viewModel.toast(websiteToast) },
                onPrivacy = { viewModel.toast(privacyToast) }
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
private fun AboutHeader(onBack: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 14.dp)
    ) {
        Box(
            modifier = Modifier
                // Back button nudged 8px left, matching the reference header.
                .offset(x = (-8).dp)
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
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.prof_about_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.6).sp,
            color = colors.ink
        )
    }
}

@Composable
private fun AboutIdentityCard() {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val appVersion = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .cardShape()
            .padding(horizontal = 16.dp, vertical = 28.dp)
    ) {
        Row {
            Text(
                text = "Safe",
                fontFamily = SerifFamily,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink
            )
            Text(
                text = "Me",
                fontFamily = SerifFamily,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = colors.brand
            )
        }
        Text(
            text = stringResource(R.string.prof_about_version, appVersion, context.packageName),
            fontSize = 13.sp,
            color = colors.ink2,
            modifier = Modifier.padding(top = 6.dp)
        )
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .clip(CircleShape)
                .background(colors.successBg)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = stringResource(R.string.prof_footer_2),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.success
            )
        }
    }
}

@Composable
private fun AboutLinksList(
    onWebsite: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .cardShape()
    ) {
        AboutRow(
            icon = ProfLinkIcon,
            background = colors.brandSoft,
            tint = colors.brandDark,
            title = stringResource(R.string.prof_about_deeplink_title),
            sub = stringResource(R.string.prof_about_deeplink_sub),
            showChevron = false
        )
        HorizontalDivider(color = colors.line)
        AboutRow(
            icon = ProfGlobeIcon,
            background = colors.brandSoft,
            tint = colors.brandDark,
            title = stringResource(R.string.prof_about_website),
            onClick = onWebsite
        )
        HorizontalDivider(color = colors.line)
        AboutRow(
            icon = ProfShieldIcon,
            background = colors.iconGreenBg,
            tint = colors.success,
            title = stringResource(R.string.prof_about_privacy),
            onClick = onPrivacy
        )
    }
}

@Composable
private fun AboutRow(
    icon: ImageVector,
    background: Color,
    tint: Color,
    title: String,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = true,
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
            if (sub != null) {
                Text(
                    text = sub,
                    fontSize = 12.sp,
                    color = colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
        }
        if (showChevron) {
            Icon(
                imageVector = ProfChevIcon,
                contentDescription = null,
                tint = colors.ink3,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
