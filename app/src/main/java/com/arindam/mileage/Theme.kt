package com.arindam.mileage

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.*

val FuelBlue = Color(0xFF0D6EFD)
val MileageGreen = Color(0xFF28A745)
val ServiceAmber = Color(0xFFFFC107)
val DangerRed = Color(0xFFDC3545)
private val Dark = darkColorScheme(
    primary = FuelBlue, onPrimary = Color.White, secondary = MileageGreen,
    background = Color(0xFF121212), onBackground = Color(0xFFF8F9FA),
    surface = Color(0xFF1A1A1A), onSurface = Color(0xFFF8F9FA),
    surfaceVariant = Color(0xFF242629), onSurfaceVariant = Color(0xFFA1A7B0),
    outline = Color(0xFF535860), outlineVariant = Color(0xFF303338), error = Color(0xFFFF707C)
)
private val Light = lightColorScheme(
    primary = FuelBlue, onPrimary = Color.White, secondary = Color(0xFF218838),
    background = Color(0xFFF0F2F5), onBackground = Color(0xFF212529),
    surface = Color.White, onSurface = Color(0xFF212529),
    surfaceVariant = Color(0xFFE7EAEE), onSurfaceVariant = Color(0xFF626A73),
    outlineVariant = Color(0xFFDADDE2)
)
@OptIn(ExperimentalTextApi::class)
private val Outfit = FontFamily(*listOf(400, 500, 600, 700).map { weight ->
    Font(R.font.outfit, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
}.toTypedArray())
private val Typography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontFamily = Outfit, fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontFamily = Outfit, fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontFamily = Outfit, fontWeight = FontWeight.Medium),
        bodyLarge = t.bodyLarge.copy(fontFamily = Outfit), bodyMedium = t.bodyMedium.copy(fontFamily = Outfit),
        bodySmall = t.bodySmall.copy(fontFamily = Outfit), labelLarge = t.labelLarge.copy(fontFamily = Outfit),
        labelMedium = t.labelMedium.copy(fontFamily = Outfit), labelSmall = t.labelSmall.copy(fontFamily = Outfit)
    )
}
@Composable
fun MileageTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Typography, shapes = Shapes(extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)), content = content)
}
@Composable
fun ReferenceBackground(content: @Composable BoxScope.() -> Unit) {
    val dark = MaterialTheme.colorScheme.background == Dark.background
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .background(Brush.radialGradient(listOf(FuelBlue.copy(alpha = if (dark) .12f else .07f), Color.Transparent), center = Offset(0f, 0f), radius = 1200f))
        .background(Brush.linearGradient(listOf(Color.Transparent, MileageGreen.copy(alpha = if (dark) .06f else .05f)))), content = content)
}
