package com.safeme.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeme.app.data.AppCategory
import com.safeme.app.data.InstalledApp
import com.safeme.app.ui.theme.LocalAppColors

/**
 * Shared App Picker list used by every picker in the app (schedule apps, VPN
 * whitelist). Matches the prototype's grouped picker: each category renders as
 * an uppercase label above its own rounded bordered box of rows (`.app-group`:
 * `.group-label` + `.list`), with 16dp between groups. The row rendering stays
 * screen-specific via [row] so each picker keeps its own design language; only
 * the grouping/presentation is shared.
 *
 * [groups] comes from `AppCatalog.groupApps(...)`, which handles search
 * filtering and hides empty categories.
 */
@Composable
fun GroupedAppPickerList(
    groups: List<Pair<AppCategory, List<InstalledApp>>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    row: @Composable (app: InstalledApp, checked: Boolean, onToggle: () -> Unit) -> Unit,
) {
    val colors = LocalAppColors.current
    LazyColumn(
        modifier = modifier,
        verticalArrangement = verticalArrangement,
    ) {
        groups.forEach { (category, apps) ->
            item(key = "group-${category.name}") {
                Column {
                    AppCategoryHeader(category = category)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.line, RoundedCornerShape(20.dp)),
                    ) {
                        apps.forEachIndexed { appIndex, app ->
                            row(app, app.packageName in selected) {
                                onToggle(app.packageName)
                            }
                            if (appIndex < apps.lastIndex) {
                                HorizontalDivider(color = colors.line)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Prototype `.app-group .group-label`: uppercase, 11.5sp/800, ink-3, 2dp
 * horizontal inset (the prototype's `margin: 0 2px 8px`), 8dp below the label.
 * The 16dp gap between groups comes from the picker list's vertical
 * arrangement (`.app-group + .app-group { margin-top: 16px }`).
 */
@Composable
fun AppCategoryHeader(category: AppCategory) {
    val colors = LocalAppColors.current
    Text(
        text = stringResource(category.labelRes).uppercase(),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.6.sp,
        color = colors.ink3,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 2.dp, bottom = 8.dp),
    )
}
