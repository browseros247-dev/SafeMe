package com.safeme.app.ui.screens.blockscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeme.app.R
import com.safeme.app.ui.components.SafeMeTextField
import com.safeme.app.ui.components.blurredShadow
import com.safeme.app.ui.theme.LocalAppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MotivationImageSheet(
    current: String,
    onCancel: () -> Unit,
    onDone: (String) -> Unit,
) {
    var selected by remember { mutableStateOf(current) }
    val sheetState = rememberModalBottomSheetState()
    val colors = LocalAppColors.current
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
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                SheetGrab()
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.bs_motivation_image),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.bs_img_sub_sheet),
                    fontSize = 13.sp,
                    color = colors.ink2
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ImgTile(
                        colors = null,
                        label = stringResource(R.string.bs_no_image),
                        selected = selected.isEmpty(),
                        onClick = { selected = "" },
                        modifier = Modifier.weight(1f)
                    )
                    ImgTile(
                        colors = bsImgColors("sunset"),
                        label = null,
                        selected = selected == "sunset",
                        onClick = { selected = "sunset" },
                        modifier = Modifier.weight(1f)
                    )
                    ImgTile(
                        colors = bsImgColors("ocean"),
                        label = null,
                        selected = selected == "ocean",
                        onClick = { selected = "ocean" },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ImgTile(
                        colors = bsImgColors("forest"),
                        label = null,
                        selected = selected == "forest",
                        onClick = { selected = "forest" },
                        modifier = Modifier.weight(1f)
                    )
                    ImgTile(
                        colors = bsImgColors("aurora"),
                        label = null,
                        selected = selected == "aurora",
                        onClick = { selected = "aurora" },
                        modifier = Modifier.weight(1f)
                    )
                    ImgTile(
                        colors = bsImgColors("mono"),
                        label = null,
                        selected = selected == "mono",
                        onClick = { selected = "mono" },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ImgTile(
                        colors = bsImgColors("peak"),
                        label = null,
                        selected = selected == "peak",
                        onClick = { selected = "peak" },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            SheetRow2(
                cancelLabel = stringResource(R.string.bs_cancel),
                confirmLabel = stringResource(R.string.bs_done),
                onCancel = onCancel,
                onConfirm = { onDone(selected) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomMessageSheet(
    current: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    val sheetState = rememberModalBottomSheetState()
    val colors = LocalAppColors.current
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
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                SheetGrab()
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.bs_custom_message),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.bs_msg_sub_sheet),
                    fontSize = 13.sp,
                    color = colors.ink2
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            SheetField(icon = MessageIcon) { focusRequester ->
                SafeMeTextField(
                    value = text,
                    onValueChange = { if (it.length <= 60) text = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        color = colors.ink
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.bs_preview_msg_default),
                                fontSize = 15.sp,
                                color = colors.ink3
                            )
                        }
                        innerTextField()
                    }
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.bs_suggestions).uppercase(),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp,
                color = colors.ink3,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Stay safe, Alex",
                    "Stay focused",
                    "You chose this",
                    "Your goal matters",
                    "Step away"
                ).forEach { chip ->
                    val isSelected = text == chip
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.brandSoft else colors.surface)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) colors.brand else colors.line,
                                shape = CircleShape
                            )
                            .clickable { text = chip }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chip,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) colors.brandDark else colors.ink2
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            SheetRow2(
                cancelLabel = stringResource(R.string.bs_cancel),
                confirmLabel = stringResource(R.string.bs_save),
                onCancel = onCancel,
                onConfirm = { onSave(text.trim()) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RedirectUrlSheet(
    current: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    val sheetState = rememberModalBottomSheetState()
    val colors = LocalAppColors.current
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
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                SheetGrab()
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.bs_redirect_url),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.bs_url_sub_sheet),
                    fontSize = 13.sp,
                    color = colors.ink2
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            SheetField(icon = LinkIcon) { focusRequester ->
                SafeMeTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp,
                        color = colors.ink
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.bs_url_placeholder),
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
                    imageVector = InfoIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp),
                    tint = colors.ink3
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.bs_url_note),
                    fontSize = 12.sp,
                    color = colors.ink2,
                    lineHeight = 18.sp
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            SheetRow2(
                cancelLabel = stringResource(R.string.bs_cancel),
                confirmLabel = stringResource(R.string.bs_save),
                onCancel = onCancel,
                onConfirm = { onSave(text.trim()) }
            )
        }
    }
}

@Composable
private fun SheetGrab() {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 5.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.line)
    )
}

@Composable
private fun SheetField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable (FocusRequester) -> Unit,
) {
    val colors = LocalAppColors.current
    val focusRequester = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            // Whole-field tap-to-focus: the BasicTextField is content-sized via
            // weight(1f), so without this only the text area would take focus
            // and the icon strip would be dead.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { focusRequester.requestFocus() }
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
        content(focusRequester)
    }
}

@Composable
private fun SheetRow2(
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
private fun ImgTile(
    colors: List<Color>?,
    label: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalAppColors.current
    val shape = RoundedCornerShape(14.dp)
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(4f / 3f)
            .clip(shape)
            .background(if (colors != null) Color.Transparent else palette.brandSoft)
            .then(if (selected) Modifier.border(2.dp, palette.brand, shape) else Modifier.border(2.dp, Color.Transparent, shape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (colors != null) {
            val brush = with(LocalDensity.current) {
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = colors,
                    start = androidx.compose.ui.geometry.Offset.Zero,
                    end = androidx.compose.ui.geometry.Offset(maxWidth.toPx(), maxHeight.toPx())
                )
            }
            Box(modifier = Modifier.matchParentSize().clip(shape).background(brush))
        } else if (label != null) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) palette.brandDark else palette.ink2
            )
        }
    }
}
