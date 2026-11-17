package com.aribs.rsenhancify.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

object GlassColors {
    val BgTop = Color(0xFF0B0A1F)
    val BgBottom = Color(0xFF120B2E)
    val Violet = Color(0xFF7B2FF7)
    val Cyan = Color(0xFF00C2FF)
    val Pink = Color(0xFFFF4FA3)
    val Amber = Color(0xFFFFB547)
    val Mint = Color(0xFF2EE6A6)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xB3FFFFFF)
    val TextMuted = Color(0x80FFFFFF)
    val GlassFill = Color(0x1AFFFFFF)
    val GlassFillStrong = Color(0x29FFFFFF)
    val Error = Color(0xFFFF6B6B)
}

val GlassShape = RoundedCornerShape(24.dp)

/** Deep gradient background with slowly drifting soft colour blobs (the "glass" needs something behind it). */
@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "bg")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(GlassColors.BgTop, GlassColors.BgBottom)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun blob(color: Color, cx: Float, cy: Float, r: Float) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0f)),
                        center = Offset(cx, cy),
                        radius = r
                    ),
                    radius = r,
                    center = Offset(cx, cy)
                )
            }
            blob(GlassColors.Violet, w * (0.2f + 0.1f * cos(phase)), h * (0.18f + 0.05f * sin(phase)), w * 0.75f)
            blob(GlassColors.Cyan, w * (0.9f + 0.08f * sin(phase)), h * (0.45f + 0.06f * cos(phase)), w * 0.65f)
            blob(GlassColors.Pink, w * (0.25f + 0.1f * sin(phase + 1f)), h * (0.85f + 0.04f * cos(phase)), w * 0.7f)
        }
        content()
    }
}

fun Modifier.glass(shape: androidx.compose.ui.graphics.Shape = GlassShape, strong: Boolean = false): Modifier =
    this
        .clip(shape)
        .background(
            Brush.linearGradient(
                listOf(
                    if (strong) GlassColors.GlassFillStrong else GlassColors.GlassFill,
                    Color(0x0DFFFFFF)
                )
            )
        )
        .border(
            1.dp,
            Brush.linearGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF), Color(0x40FFFFFF))),
            shape
        )

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .glass()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content
    )
}

@Composable
fun GlassTopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(44.dp)
                    .glass(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
        }
        Column {
            Text(title, color = GlassColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, color = GlassColors.TextSecondary, fontSize = 13.sp)
        }
    }
}

/** Primary action button: vivid gradient pill. */
@Composable
fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    colors: List<Color> = listOf(GlassColors.Violet, GlassColors.Cyan),
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .height(54.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .background(Brush.horizontalGradient(colors))
            .border(1.dp, Color(0x55FFFFFF), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

/** Secondary action: frosted pill. */
@Composable
fun GlassOutlineButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier
            .height(50.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .glass(RoundedCornerShape(16.dp), strong = true)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}

/** Segmented chooser rendered as glass chips. */
@Composable
fun <T> GlassSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean = true,
    onSelect: (T) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(16.dp))
            .padding(4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSel = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (isSel) Modifier.background(
                            Brush.horizontalGradient(listOf(GlassColors.Violet.copy(alpha = 0.85f), GlassColors.Cyan.copy(alpha = 0.85f)))
                        ) else Modifier
                    )
                    .clickable(enabled = enabled) { onSelect(value) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (isSel) Color.White else GlassColors.TextSecondary,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = GlassColors.TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 6.dp)
    )
}

@Composable
fun GlassProgress(label: String, progress: Float?, onCancel: (() -> Unit)? = null) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (progress != null) {
                Text("${(progress * 100).toInt()}%", color = GlassColors.TextSecondary, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = GlassColors.Cyan,
                trackColor = Color(0x22FFFFFF),
                strokeCap = StrokeCap.Round
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = GlassColors.Cyan,
                trackColor = Color(0x22FFFFFF),
                strokeCap = StrokeCap.Round
            )
        }
        if (onCancel != null) {
            Spacer(Modifier.height(12.dp))
            GlassOutlineButton("Cancel", Modifier.fillMaxWidth(), onClick = onCancel)
        }
    }
}

@Composable
fun MessageCard(text: String, isError: Boolean = false) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(text, color = if (isError) GlassColors.Error else GlassColors.Mint, fontSize = 14.sp)
    }
}

@Composable
fun RowScope.Gap(w: Dp = 10.dp) = Spacer(Modifier.width(w))

@Composable
fun ColumnScope.VGap(h: Dp = 12.dp) = Spacer(Modifier.height(h))
