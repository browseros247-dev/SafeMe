package com.safeme.app.ui.screens.vpn

import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.safeme.app.R
import com.safeme.app.ui.components.blurredShadow
import com.safeme.app.ui.theme.LocalAppColors
import com.safeme.app.vpn.VpnValidation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDnsSheet(
    v4: String,
    v6: String,
    onCancel: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val colors = LocalAppColors.current
    var v4Text by remember { mutableStateOf(v4) }
    var v6Text by remember { mutableStateOf(v6) }
    var showError by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 6.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VpnSheetGrab()
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn_sheet_dns_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.vpn_sheet_dns_sub),
                    fontSize = 13.sp,
                    color = colors.ink2
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            VpnSheetField(icon = VpnFieldShieldIcon) {
                BasicTextField(
                    value = v4Text,
                    onValueChange = { if (it.length <= 45) { v4Text = it; showError = false } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        color = colors.ink
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (v4Text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.vpn_dns_v4_placeholder),
                                fontSize = 15.sp,
                                color = colors.ink3
                            )
                        }
                        innerTextField()
                    }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            VpnSheetField(icon = VpnFieldShieldIcon) {
                BasicTextField(
                    value = v6Text,
                    onValueChange = { if (it.length <= 60) { v6Text = it; showError = false } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        color = colors.ink
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (v6Text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.vpn_dns_v6_placeholder),
                                fontSize = 15.sp,
                                color = colors.ink3
                            )
                        }
                        innerTextField()
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = VpnInfoIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp),
                    tint = colors.ink3
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.vpn_dns_note),
                    fontSize = 12.sp,
                    color = colors.ink2,
                    lineHeight = 18.sp
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            if (showError) {
                Text(
                    text = stringResource(R.string.vpn_dns_invalid),
                    fontSize = 12.sp,
                    color = colors.danger,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            VpnSheetRow2(
                cancelLabel = stringResource(R.string.vpn_cancel),
                confirmLabel = stringResource(R.string.vpn_save),
                onCancel = onCancel,
                onConfirm = {
                    val cleanV4 = v4Text.trim()
                    val cleanV6 = v6Text.trim()
                    val valid = VpnValidation.isValidIpv4(cleanV4) &&
                        (cleanV6.isEmpty() || VpnValidation.isValidIpv6(cleanV6))
                    if (valid) {
                        showError = false
                        onSave(v4Text, v6Text)
                    } else {
                        showError = true
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnAppsSheet(
    apps: List<AppInfo>,
    loading: Boolean = false,
    whitelist: Set<String>,
    onToggle: (String) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    var query by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 6.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VpnSheetGrab()
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn_sheet_apps_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.vpn_sheet_apps_sub),
                    fontSize = 13.sp,
                    color = colors.ink2
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            VpnSheetField(icon = VpnSearchIcon) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        color = colors.ink
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.vpn_app_search_placeholder),
                                fontSize = 15.sp,
                                color = colors.ink3
                            )
                        }
                        innerTextField()
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            val filtered = remember(apps, query) {
                val q = query.trim().lowercase()
                if (q.isEmpty()) apps else apps.filter { it.label.lowercase().contains(q) }
            }
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) {
                        Text(
                            text = stringResource(R.string.vpn_app_loading),
                            fontSize = 13.sp,
                            color = colors.ink2,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.vpn_app_no_results),
                            fontSize = 13.sp,
                            color = colors.ink2
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            checked = app.packageName in whitelist,
                            onClick = { onToggle(app.packageName) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            VpnDoneButton(text = stringResource(R.string.vpn_done), onClick = onDone)
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, checked: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    // Icon loading is slow (PackageManager + bitmap decode); do it off the main
    // thread so scrolling the whitelist sheet never janks.
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null, key1 = app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable: Drawable = context.packageManager.getApplicationIcon(app.packageName)
                drawable.toBitmap(48, 48).asImageBitmap()
            }.getOrNull()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.brandSoft),
            contentAlignment = Alignment.Center
        ) {
            icon?.let { loadedIcon ->
                androidx.compose.foundation.Image(
                    bitmap = loadedIcon,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = app.label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colors.ink,
            modifier = Modifier.weight(1f)
        )
        VpnCheckbox(checked = checked)
    }
}

@Composable
internal fun VpnCheckbox(checked: Boolean) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) colors.brand else colors.surface)
            .border(
                width = 2.dp,
                color = if (checked) colors.brand else colors.ink3,
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = VpnCheckIcon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun VpnSheetGrab() {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 5.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.line)
        )
    }
}

@Composable
private fun VpnSheetField(icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = colors.ink3
        )
        Spacer(modifier = Modifier.width(10.dp))
        content()
    }
}

@Composable
private fun VpnSheetRow2(
    cancelLabel: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(CircleShape)
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center
        ) {
            Text(text = cancelLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.ink2)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .blurredShadow(
                    cornerRadius = 26.dp,
                    color = colors.brand.copy(alpha = 0.35f),
                    blurRadius = 20.dp,
                    offsetY = 8.dp
                )
                .clip(CircleShape)
                .background(colors.brand)
                .clickable(onClick = onConfirm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = confirmLabel,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun VpnDoneButton(text: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .blurredShadow(
                cornerRadius = 26.dp,
                color = colors.brand.copy(alpha = 0.35f),
                blurRadius = 20.dp,
                offsetY = 8.dp
            )
            .clip(CircleShape)
            .background(colors.brand)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}
