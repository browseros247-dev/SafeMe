package com.safeme.app.ui.screens.antitamper

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safeme.app.R
import com.safeme.app.protect.ProtectedServiceEntry
import com.safeme.app.ui.screens.permissions.ChevronIcon
import com.safeme.app.ui.theme.LocalAppColors

/**
 * "Protect another app's Accessibility Service" picker — every installed
 * third-party Accessibility Service (SafeMe's own is always protected and
 * shown on the parent screen, so it is excluded here), with a search box.
 * Each eligible app is a card with its real icon, name, and an ON/OFF switch
 * that controls whether its service is included in the protection mechanism.
 */
@Composable
fun ServicePickerScreen(
    onBack: () -> Unit,
    viewModel: ServicePickerViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = LocalAppColors.current

    val query = state.query.trim()
    val filtered = if (query.isEmpty()) {
        state.services
    } else {
        state.services.filter {
            it.appLabel.contains(query, ignoreCase = true) ||
                it.serviceClass.contains(query, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .offset(x = (-8).dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = ChevronIcon,
                    contentDescription = stringResource(R.string.perm_back),
                    modifier = Modifier.size(20.dp),
                    tint = colors.ink,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.ap_picker_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.ink,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.ap_picker_sub),
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = colors.ink2,
            )
        }
        Spacer(Modifier.height(14.dp))
        SearchField(
            query = state.query,
            onQuery = { viewModel.setQuery(it) },
        )
        Spacer(Modifier.height(12.dp))
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.ap_picker_empty),
                    fontSize = 13.sp,
                    color = colors.ink2,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.flatComponent }) { entry ->
                    PickerRow(
                        entry = entry,
                        protected = entry.flatComponent in state.protected,
                        onToggle = { viewModel.setProtected(entry.flatComponent, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AtSearchIcon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colors.ink3,
        )
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQuery,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = colors.ink,
            ),
            singleLine = true,
            cursorBrush = SolidColor(colors.ink),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 13.dp),
        )
        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQuery("") },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = AtXIcon,
                    contentDescription = stringResource(R.string.ap_picker_search),
                    modifier = Modifier.size(15.dp),
                    tint = colors.ink3,
                )
            }
        }
    }
}

/** One eligible app: real icon + name + ON/OFF protection switch. */
@Composable
private fun PickerRow(
    entry: ProtectedServiceEntry,
    protected: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val colors = LocalAppColors.current
    // Never let a pathological app drawable crash the list during composition;
    // fall back to the generic shield icon if conversion fails.
    val iconBitmap = remember(entry.icon) {
        runCatching { entry.icon?.toBitmap(128, 128)?.asImageBitmap() }.getOrNull()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .border(
                1.dp,
                if (protected) colors.brand.copy(alpha = 0.5f) else colors.line,
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface.copy(alpha = 0.7f))
                .border(1.dp, colors.line, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)),
                )
            } else {
                Icon(
                    imageVector = AtShieldPlusIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.ink2,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.appLabel,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
                maxLines = 1,
            )
            Text(
                text = entry.serviceClass.substringAfterLast('.'),
                fontSize = 11.5.sp,
                color = colors.ink2,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.size(10.dp))
        MasterSwitch(
            checked = protected,
            onToggle = { onToggle(!protected) },
        )
    }
}

/** Replica of the Blocking screen's animated switch. */
@Composable
private fun MasterSwitch(checked: Boolean, onToggle: () -> Unit) {
    val colors = LocalAppColors.current
    val bg by animateColorAsState(
        targetValue = if (checked) colors.brand else colors.swOff,
        animationSpec = tween(200),
        label = "switchBg",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 21.dp else 0.dp,
        animationSpec = tween(200),
        label = "switchThumb",
    )
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 31.dp)
            .clip(CircleShape)
            .background(bg)
            .semantics { role = Role.Switch }
            .clickable(role = Role.Switch, onClick = onToggle),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = 3.dp + thumbOffset)
                .size(25.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}
