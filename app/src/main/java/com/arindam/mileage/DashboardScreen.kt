package com.arindam.mileage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(data: ScreenData, add: () -> Unit, viewAll: () -> Unit) {
    val insights = data.insights
    val currency = data.preferences.currency
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item("monthly-spent") { QuickStat("Monthly Fuel Spent", money(insights.current.spent, currency), Icons.Outlined.Payments) }
        item("average-distance") { QuickStat("Avg Monthly Dist", "${shortNumber(insights.averageMonthlyDistance.toBigDecimal())} km", Icons.Outlined.Route) }
        item("average-efficiency") { QuickStat("Avg Efficiency", "${formatMileage(insights.dashboardMileage)} km/L", Icons.Outlined.Speed, MileageGreen) }
        item("analytics-heading") { SectionHeading("Analytics", "Swipe to see detailed trends") }
        item("analytics-carousel") { TrendCarousel(insights, currency) }
        item("recent-heading") {
            SectionHeading("Recent Logs", "Last 10 entries") {
                TextButton(onClick = viewAll) { Text("View All"); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 4.dp).size(16.dp)) }
            }
        }
        if (data.fuel.isEmpty() && data.loaded) item("empty") {
            EmptyHistory("Your journey starts here", "Log a fill-up or import your fuel history from Settings.", Icons.Outlined.LocalGasStation, add)
        }
        items(data.fuel.take(10), key = { "recent-${it.id}" }) { fuel ->
            FuelRecordCard(fuel, currency, data.perFill[fuel.id], onOpen = viewAll)
        }
    }
}
@Composable
private fun QuickStat(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = FuelBlue) {
    GlassCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Metric(label, value, Modifier.weight(1f))
            IconTile(icon, tint)
        }
    }
}
@Composable
fun TrendCarousel(insights: Insights, currency: String) {
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val titles = listOf("Fuel Price Trend", "Running Cost", "Efficiency Trend")
    Column {
        HorizontalPager(state = pager, key = { it }, pageSpacing = 16.dp, modifier = Modifier.fillMaxWidth()) { page ->
            val values = when (page) { 0 -> insights.priceTrend; 1 -> insights.runningCost; else -> insights.efficiencyTrend }
            val tint = if (page == 2) MileageGreen else MaterialTheme.colorScheme.onSurface
            fun value(v: Double?) = if (v == null) "—" else when (page) {
                0 -> "${currencySymbol(currency)}${formatMileage(v)}"
                1 -> "${currencySymbol(currency)}${formatMileage(v)}/km"
                else -> "${formatMileage(v)} km/L"
            }
            GlassCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(titles[page], Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Badge("Avg: ${value(values.takeIf { it.isNotEmpty() }?.average())}", tint)
                }
                TrendChart(values, titles[page], tint)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth()) {
                    listOf("↓ Lowest", "↑ Highest", "— Average").forEachIndexed { i, label ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val labelTint = when (i) { 0 -> if (page == 2) DangerRed else MileageGreen; 1 -> if (page == 2) MileageGreen else DangerRed; else -> MaterialTheme.colorScheme.onSurfaceVariant }
                            Text(label, color = labelTint, style = MaterialTheme.typography.labelSmall)
                            val v = if (values.isEmpty()) null else when (i) { 0 -> values.min(); 1 -> values.max(); else -> values.average() }
                            Text(value(v), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(3) { page ->
                IconButton(onClick = { scope.launch { pager.animateScrollToPage(page) } }, modifier = Modifier.semantics { contentDescription = "Show ${titles[page]}" }) {
                    Surface(Modifier.size(24.dp, 4.dp), shape = RoundedCornerShape(4.dp), color = if (pager.currentPage == page) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant) {}
                }
            }
        }
    }
}
