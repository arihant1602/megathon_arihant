package org.seedhecode.incidentassistant.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import org.seedhecode.incidentassistant.feature.report.CaptureState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val MidnightNavy = Color(0xFF06103C)
private val DeepIndigo = Color(0xFF1B2A6B)
private val ElectricBlue = Color(0xFF007AFF)
private val SoftViolet = Color(0xFF8A51FC)
private val HighlightWhite = Color(0x60FFFFFF)

@Composable
fun AuroraVoiceOrb(
    audioLevel: Float,
    captureState: CaptureState,
    modifier: Modifier = Modifier
) {
    // Reduced motion: the system "remove animations" setting sets the animator duration scale to 0.
    val context = androidx.compose.ui.platform.LocalContext.current
    val reducedMotion = androidx.compose.runtime.remember {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    var smoothedLevel by remember { mutableFloatStateOf(0f) }

    // Damped interpolation with fast attack (rising level) and slow, graceful decay (silence)
    LaunchedEffect(audioLevel, captureState) {
        if (captureState == CaptureState.RECORDING && !reducedMotion) {
            val target = audioLevel.coerceIn(0f, 1f)
            val isRising = target > smoothedLevel
            val duration = if (isRising) 60 else 320 // 60ms fast attack, 320ms slow decay
            animate(
                initialValue = smoothedLevel,
                targetValue = target,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
            ) { value, _ ->
                smoothedLevel = value
            }
        } else {
            smoothedLevel = 0f
        }
    }

    var phase by remember { mutableFloatStateOf(0f) }
    val isAnimating = (captureState == CaptureState.RECORDING || captureState == CaptureState.IDLE) && !reducedMotion

    LaunchedEffect(isAnimating) {
        if (isAnimating) {
            var lastFrameNanos = 0L
            while (isActive) {
                withFrameNanos { frameNanos ->
                    if (lastFrameNanos != 0L) {
                        val dt = (frameNanos - lastFrameNanos) / 1_000_000_000f
                        val speed = if (captureState == CaptureState.RECORDING) 1.2f + smoothedLevel * 2.5f else 0.4f
                        phase = (phase + dt * speed) % (2f * PI.toFloat())
                    }
                    lastFrameNanos = frameNanos
                }
            }
        }
    }

    // Reuse Path allocations outside draw loop
    val fluidPath = remember { Path() }
    val innerPath = remember { Path() }

    Box(
        modifier = modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (minOf(size.width, size.height) / 2f) * 0.72f
            val reactiveRadius = baseRadius * (1f + smoothedLevel * 0.28f)

            // 1. Outer Glow Brush
            val glowBrush = Brush.radialGradient(
                colors = listOf(
                    ElectricBlue.copy(alpha = 0.35f + smoothedLevel * 0.3f),
                    DeepIndigo.copy(alpha = 0.15f),
                    Color.Transparent
                ),
                center = center,
                radius = reactiveRadius * 1.35f
            )
            drawCircle(brush = glowBrush, radius = reactiveRadius * 1.35f, center = center)

            // 2. Main Atmosphere Fluid Globe Brush
            val mainBrush = Brush.radialGradient(
                colors = listOf(
                    SoftViolet.copy(alpha = 0.85f),
                    ElectricBlue.copy(alpha = 0.9f),
                    DeepIndigo,
                    MidnightNavy
                ),
                center = Offset(center.x - reactiveRadius * 0.2f, center.y - reactiveRadius * 0.2f),
                radius = reactiveRadius * 1.2f
            )

            // 3. Procedural Ripple Path for Recording Waveform Outer Boundary
            if (captureState == CaptureState.RECORDING && smoothedLevel > 0.01f) {
                fluidPath.reset()
                val numPoints = 12
                val rippleAmp = reactiveRadius * (0.04f + smoothedLevel * 0.12f)
                for (i in 0 until numPoints) {
                    val angle = (i.toFloat() / numPoints) * 2f * PI.toFloat()
                    val wave = sin(angle * 3f + phase * 2f) * cos(angle * 2f - phase)
                    val r = reactiveRadius + wave * rippleAmp
                    val x = center.x + r * cos(angle)
                    val y = center.y + r * sin(angle)
                    if (i == 0) fluidPath.moveTo(x, y) else fluidPath.lineTo(x, y)
                }
                fluidPath.close()
                drawPath(path = fluidPath, brush = mainBrush)
            } else {
                // Smooth Circle for Rest / Breathing State
                val breathingRadius = if (captureState == CaptureState.IDLE) {
                    baseRadius * (1f + sin(phase) * 0.03f)
                } else {
                    baseRadius
                }
                drawCircle(brush = mainBrush, radius = breathingRadius, center = center)
            }

            // 4. Secondary Wave Layer (Inner Translucent Swirl)
            if (captureState == CaptureState.RECORDING) {
                innerPath.reset()
                val innerRadius = reactiveRadius * 0.75f
                val numPoints = 8
                val innerAmp = innerRadius * (0.05f + smoothedLevel * 0.15f)
                for (i in 0 until numPoints) {
                    val angle = (i.toFloat() / numPoints) * 2f * PI.toFloat()
                    val wave = sin(angle * 2f - phase * 1.5f)
                    val r = innerRadius + wave * innerAmp
                    val x = center.x + r * cos(angle)
                    val y = center.y + r * sin(angle)
                    if (i == 0) innerPath.moveTo(x, y) else innerPath.lineTo(x, y)
                }
                innerPath.close()
                drawPath(
                    path = innerPath,
                    brush = Brush.radialGradient(
                        colors = listOf(SoftViolet.copy(alpha = 0.6f), ElectricBlue.copy(alpha = 0.2f)),
                        center = center,
                        radius = innerRadius
                    )
                )
            }

            // 5. Glass Highlight Sheen
            val highlightRadius = reactiveRadius * 0.25f
            val highlightCenter = Offset(center.x - reactiveRadius * 0.35f, center.y - reactiveRadius * 0.35f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(HighlightWhite, Color.Transparent),
                    center = highlightCenter,
                    radius = highlightRadius
                ),
                radius = highlightRadius,
                center = highlightCenter
            )

            // 6. Outer Luminous Ring
            drawCircle(
                color = ElectricBlue.copy(alpha = 0.4f + smoothedLevel * 0.4f),
                radius = reactiveRadius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
