package com.arindam.mileage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AnalyticsScreen(data: ScreenData) {
    val stats = data.insights
    val currency = data.preferences.currency
    val monthFormat = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    val savings = stats.previous.spent - stats.current.spent
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item("this-month") {
            GlassCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("This Month", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    Badge(stats.month.format(monthFormat))
                }
                MonthMetrics(stats.current, currency)
            }
        }
        item("savings") {
            GlassCard {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(if (savings.signum() < 0) Icons.AutoMirrored.Outlined.TrendingUp else Icons.AutoMirrored.Outlined.TrendingDown, if (savings.signum() < 0) DangerRed else MileageGreen)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(when (savings.signum()) { 1 -> "Great Savings!"; -1 -> "Spending Increased"; else -> "On Track" }, style = MaterialTheme.typography.titleMedium)
                        val percentage = if (stats.previous.spent.signum() > 0) savings.abs().toDouble() / stats.previous.spent.toDouble() * 100 else 0.0
                        Text(when (savings.signum()) {
                            1 -> "Saved ${money(savings, currency)} (${String.format(Locale.ROOT, "%.0f", percentage)}% less) compared to previous month."
                            -1 -> "Spent ${money(savings.abs(), currency)} (${String.format(Locale.ROOT, "%.0f", percentage)}% more) compared to previous month."
                            else -> "Spending is exactly the same as previous month."
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item("comparison-title") { SectionHeading("Monthly Comparison") }
        item("comparison") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(listOf(stats.month to stats.current, stats.month.minusMonths(1) to stats.previous), key = { it.first.toString() }) { (month, period) ->
                    ComparisonCard(month.format(monthFormat), period, currency, Modifier.width(260.dp))
                }
            }
        }
        item("year-title") { SectionHeading("${stats.month.year} All-Time Overview") }
        item("year-stats") {
            GlassCard {
                Text("TOTAL SPEND", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(stats.year.spent, currency), style = MaterialTheme.typography.headlineLarge)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StatRow("Distance", "${shortNumber(stats.year.distance)} km", Icons.Outlined.Route)
                StatRow("Mileage", "${formatMileage(stats.year.mileage ?: 0.0)} km/L", Icons.Outlined.Speed)
                StatRow("Fill-ups", stats.year.fillups.toString(), Icons.Outlined.LocalGasStation)
                val average = if (stats.year.fillups > 0) stats.year.spent.divide(stats.year.fillups.toBigDecimal(), 6, java.math.RoundingMode.HALF_UP) else BigDecimal.ZERO
                StatRow("Avg Cost / Fill", money(average, currency), Icons.Outlined.Payments)
            }
        }
        item("all-time-title") { SectionHeading("All-Time Overview") }
        item("all-time") {
            GlassCard {
                StatRow("Total Spent", money(stats.lifetime.spent, currency), Icons.Outlined.Payments)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StatRow("Total Distance", "${shortNumber(stats.lifetime.distance)} km", Icons.Outlined.Route)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StatRow("Total Fill-ups", stats.lifetime.fillups.toString(), Icons.Outlined.LocalGasStation)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StatRow("Average Price", "${currencySymbol(currency)}${formatMileage(quotient(stats.lifetime.spent, stats.lifetime.litres) ?: 0.0)}/L", Icons.Outlined.Sell)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                StatRow("Most Used Station", stats.mostUsedStation, Icons.Outlined.Place)
            }
        }
        item("brand-title") { SectionHeading("Brand Efficiency", "Average km/L by station") }
        item("brand-chart") {
            GlassCard {
                if (stats.stationEfficiency.isEmpty()) {
                    Text("Add consecutive fill-ups with a station name to compare efficiency.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                } else {
                    val max = stats.stationEfficiency.maxOf { it.mileage }.coerceAtLeast(1.0)
                    stats.stationEfficiency.take(12).forEach { brand ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatRow(brand.name, "${formatMileage(brand.mileage)} km/L")
                            Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                                Box(Modifier.fillMaxWidth((brand.mileage / max).toFloat().coerceIn(.01f, 1f)).height(8.dp).background(MileageGreen, RoundedCornerShape(4.dp)))
                            }
                        }
                    }
                    if (stats.stationEfficiency.size > 12) Text("First 12 stations shown", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item("analytics-rule") { Text("Analytics mileage uses all litres in each period. Dashboard efficiency excludes the first fill-up's litres, matching the reference app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable
private fun MonthMetrics(stats: PeriodStats, currency: String) {
    Row {
        Metric("Spent", money(stats.spent, currency), Modifier.weight(1f), centered = true)
        Metric("Traveled", "${shortNumber(stats.distance)} km", Modifier.weight(1f), centered = true)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row {
        Metric("Avg Mileage", "${formatMileage(stats.mileage ?: 0.0)} km/L", Modifier.weight(1f), centered = true)
        Metric("Fill-ups", stats.fillups.toString(), Modifier.weight(1f), centered = true)
    }
}
@Composable
private fun ComparisonCard(month: String, stats: PeriodStats, currency: String, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Badge(month)
        StatRow("Spent", money(stats.spent, currency))
        StatRow("Mileage", "${formatMileage(stats.mileage ?: 0.0)} km/L")
        StatRow("Distance", "${shortNumber(stats.distance)} km")
        StatRow("Fill-ups", stats.fillups.toString())
    }
}
