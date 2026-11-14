package com.arindam.mileage

import android.animation.ValueAnimator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

val LocalMotionEnabled = staticCompositionLocalOf { true }
fun systemMotionEnabled() = ValueAnimator.areAnimatorsEnabled()

@Composable
fun revealProgress(key: Any, duration: Int = 240): Float {
    if (!LocalMotionEnabled.current) return 1f
    val progress = remember { Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(duration, easing = FastOutSlowInEasing))
    }
    return progress.value
}

/** Native window animations avoid duplicating a heavy editor during its entrance/exit. */
@Composable
fun DialogMotion() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    val motion = LocalMotionEnabled.current
    DisposableEffect(window, motion) {
        window?.setWindowAnimations(if (motion) R.style.MileageDialogMotion else 0)
        onDispose { }
    }
}

@Composable
fun MotionValue(value: String, content: @Composable (String) -> Unit) {
    if (LocalMotionEnabled.current) {
        AnimatedContent(value, transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) }, label = "metric value", content = { content(it) })
    } else content(value)
}

@Composable
fun EmptyIllustration(content: @Composable () -> Unit) {
    val progress = revealProgress(Unit, 200)
    Box(Modifier.graphicsLayer { alpha = progress; scaleX = .94f + .06f * progress; scaleY = scaleX }) { content() }
}
