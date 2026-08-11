package com.safeme.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safeme.app.data.AppCategory
import com.safeme.app.data.InstalledApp
import com.safeme.app.ui.theme.LocalAppColors

/**
 * Shared App Picker list used by every picker in the app (schedule apps, VPN
 * whitelist). Categories render in the fixed [AppCatalog.CATEGORY_ORDER] with
 * the same sticky header style everywhere — see [AppCategoryHeader]. The row
 * rendering stays screen-specific via [row] so each picker keeps its own
 * design language; only the grouping/presentation is shared.
 *
 * [groups] comes from `AppCatalog.groupApps(...)`, which handles search
 * filtering and hides empty categories.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupedAppPickerList(
    groups: List<Pair<AppCategory, List<InstalledApp>>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    row: @Composable (app: InstalledApp, checked: Boolean, isLastInGroup: Boolean, onToggle: () -> Unit) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = verticalArrangement,
    ) {
        groups.forEachIndexed { groupIndex, (category, apps) ->
            stickyHeader(key = "header-${category.name}") {
                AppCategoryHeader(category = category, first = groupIndex == 0)
            }
            items(apps, key = { "app-${it.packageName}" }) { app ->
                row(app, app.packageName in selected, app == apps.last()) {
                    onToggle(app.packageName)
                }
            }
        }
    }
}

/**
 * Prototype `.group-label`: uppercase, 11.5sp/800, ink-3, 16dp between groups,
 * 2dp horizontal inset (matching the prototype's `margin: 16px 2px 8px` and the
 * app's other `GroupLabel`s).
 */
@Composable
fun AppCategoryHeader(category: AppCategory, first: Boolean) {
    val colors = LocalAppColors.current
    Text(
        text = stringResource(category.labelRes).uppercase(),
        fontSize = 11.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.6.sp,
        color = colors.ink3,
        modifier = Modifier
            .fillMaxWidth()
            // Solid surface so sticky headers never show scrolled content behind them.
            .background(colors.surface)
            .padding(start = 2.dp, end = 2.dp, top = if (first) 8.dp else 16.dp, bottom = 8.dp),
    )
}


