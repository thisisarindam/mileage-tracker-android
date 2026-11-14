package com.arindam.mileage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .88f), contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
@Composable
fun SectionHeading(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        action?.invoke()
    }
}
@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface, centered: Boolean = false) {
    Column(modifier, horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = tint)
    }
}
@Composable
fun Badge(text: String, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(100.dp)) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, color = tint)
    }
}
@Composable
fun IconTile(icon: ImageVector, tint: Color, size: Int = 48) {
    Surface(color = tint.copy(alpha = .12f), shape = RoundedCornerShape(12.dp)) {
        Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp)) }
    }
}
@Composable
fun StatRow(label: String, value: String, icon: ImageVector? = null, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall, color = tint, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 180.dp))
    }
}
@Composable
fun EmptyHistory(title: String, detail: String, icon: ImageVector, action: () -> Unit, actionLabel: String = "Add Entry") {
    GlassCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(icon, FuelBlue, 56)
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(onClick = action) { Text(actionLabel) }
        }
    }
}
/** Pure local rendering. Chart semantics expose exact summaries; samples only bound drawing cost. */
@Composable
fun TrendChart(values: List<Double>, label: String, tint: Color = MaterialTheme.colorScheme.onSurface) {
    if (values.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(154.dp), contentAlignment = Alignment.Center) {
            Text("Add at least two fill-ups to see this trend", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        return
    }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val average = values.average()
    val sampled = if (values.size <= 120) values else (0..119).map { values[it * (values.lastIndex) / 119] }
    Canvas(Modifier.fillMaxWidth().height(154.dp).semantics { contentDescription = "$label chart. Lowest ${formatMileage(values.min())}, highest ${formatMileage(values.max())}, average ${formatMileage(average)}" }) {
        val low = values.min(); val high = values.max()
        val range = (high - low).takeIf { it > 0 } ?: maxOf(high * .1, 1.0)
        val bottom = low - range * .15
        val top = high + range * .15
        fun y(value: Double) = (size.height * (1 - (value - bottom) / (top - bottom))).toFloat()
        repeat(4) { i -> val pos = size.height * i / 3; drawLine(grid.copy(alpha = .6f), Offset(0f, pos), Offset(size.width, pos), 1f) }
        drawLine(grid, Offset(0f, y(average)), Offset(size.width, y(average)), 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        val path = Path()
        sampled.forEachIndexed { i, v ->
            val x = if (sampled.size == 1) size.width / 2 else size.width * i / sampled.lastIndex
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        drawPath(path, tint, style = Stroke(width = 3f))
        if (sampled.size <= 30) sampled.forEachIndexed { i, v ->
            val x = if (sampled.size == 1) size.width / 2 else size.width * i / sampled.lastIndex
            drawCircle(tint, 4.dp.toPx(), Offset(x, y(v)))
        }
    }
}
fun displayDate(date: String) = LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
