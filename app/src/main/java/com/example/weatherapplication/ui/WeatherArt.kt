package com.example.weatherapplication.ui

import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.example.weatherapplication.R
import kotlin.math.PI
import kotlin.math.sin

// Публичный API: теперь сначала пробуем статическую иконку, затем fallback на анимацию.
@Composable
fun WeatherArt(code: Int, isNight: Boolean = false, modifier: Modifier = Modifier) {
    val staticRes = iconResFor(code, isNight)
    if (staticRes != null) {
        val desc = weatherCodeDescriptionForA11y(code)
        val context = LocalContext.current
        val drawable = AppCompatResources.getDrawable(context, staticRes)
        if (drawable != null) {
            val painter = rememberDrawablePainter(drawable = drawable as Drawable?)
            Image(
                painter = painter,
                contentDescription = desc,
                modifier = modifier
            )
            return
        }
    }
    // если не удалось загрузить drawable – анимированный fallback
    WeatherArtAnimated(code = code, isNight = isNight, modifier = modifier)
}

// Маппинг к ресурсам (ночные версии пока игнорируем, можно добавить позже)
private fun iconResFor(code: Int, isNight: Boolean): Int? = when (code) {
    0 -> R.drawable.sun
    1, 2 -> R.drawable.cloudy
    3 -> R.drawable.clouds
    45, 48 -> R.drawable.foggy
    51, 53, 55 -> R.drawable.rain
    61, 63, 65 -> R.drawable.rain
    66, 67 -> R.drawable.rain
    71, 73, 75, 77 -> R.drawable.snow
    80, 81, 82 -> R.drawable.rain
    85, 86 -> R.drawable.snow
    95 -> R.drawable.storm
    96, 99 -> R.drawable.storm
    else -> R.drawable.weather_unknown
}

private fun weatherCodeDescriptionForA11y(code: Int): String = when (code) {
    0 -> "Clear"
    1,2 -> "Partly cloudy"
    3 -> "Overcast"
    45,48 -> "Fog"
    51,53,55 -> "Drizzle"
    61,63,65 -> "Rain"
    66,67 -> "Freezing rain"
    71,73,75,77 -> "Snow"
    80,81,82 -> "Showers"
    85,86 -> "Snow showers"
    95 -> "Thunderstorm"
    96,99 -> "Thunderstorm with hail"
    else -> "Unknown"
}

// ===== СТАРАЯ АНИМАЦИОННАЯ ВЕРСИЯ (fallback) ===== //
@Composable
private fun WeatherArtAnimated(code: Int, isNight: Boolean = false, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "weather")
    val sunAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sunAngle"
    )
    val cloudShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cloudShift"
    )
    val fallShift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fallShift"
    )
    val flashAlpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2200
                0f at 0
                0f at 1600
                1f at 1650
                0f at 1720
                0.8f at 1780
                0f at 2200
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "flashAlpha"
    )

    Canvas(modifier = modifier) {
        if (isNight && code in listOf(0, 1, 2)) {
            drawMoonAnimated(cloudy = (code != 0), cloudShift = cloudShift)
            return@Canvas
        }
        when (code) {
            0 -> drawSunnyAnimated(angle = sunAngle)
            1, 2 -> drawSunnyAnimated(angle = sunAngle, cloudy = true, cloudShift = cloudShift)
            3, 45, 48 -> drawCloudyAnimated(cloudShift)
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> drawRainAnimated(fallShift, cloudShift)
            71, 73, 75, 77, 85, 86 -> drawSnowAnimated(fallShift, cloudShift)
            95, 96, 99 -> drawThunderAnimated(flashAlpha, cloudShift)
            else -> drawCloudyAnimated(cloudShift)
        }
    }
}

// ===== Ниже оригинальные функции рисования (оставлены без изменений) ===== //
private fun DrawScope.drawMoonAnimated(cloudy: Boolean, cloudShift: Float) {
    val center = Offset(size.width/2, size.height/2)
    val r = size.minDimension * 0.22f
    drawCircle(color = Color(0xFFDEE2E6), radius = r, center = center)
    drawCircle(color = Color(0xFFB0BAC5), radius = r * 0.85f, center = Offset(center.x + r*0.25f, center.y - r*0.1f))
    drawCircle(Color(0xFFB0BAC5), radius = r*0.12f, center = Offset(center.x - r*0.2f, center.y - r*0.1f))
    drawCircle(Color(0xFFB0BAC5), radius = r*0.08f, center = Offset(center.x + r*0.15f, center.y + r*0.05f))
    drawCircle(Color(0xFFB0BAC5), radius = r*0.06f, center = Offset(center.x - r*0.05f, center.y + r*0.18f))

    if (cloudy) {
        val shiftPx = (cloudShift - 0.5f) * size.width * 0.3f
        drawCloud(offset = Offset(size.width*0.55f + shiftPx, size.height*0.6f))
    }
}

private fun DrawScope.drawSunnyAnimated(angle: Float, cloudy: Boolean = false, cloudShift: Float = 0f) {
    val center = Offset(size.width/2, size.height/2)
    val sunR = size.minDimension * 0.22f

    // Rays rotating
    rotate(angle, center) {
        repeat(12) { i ->
            rotate(i * 30f, center) {
                drawRect(
                    color = Color(0xFFFFD54F),
                    topLeft = Offset(center.x - 3f, center.y - sunR - 18f),
                    size = Size(6f, 18f)
                )
            }
        }
    }
    // Sun core
    drawCircle(color = Color(0xFFFFEE58), radius = sunR, center = center)
    if (cloudy) {
        val shiftPx = (cloudShift - 0.5f) * size.width * 0.3f
        drawCloud(offset = Offset(size.width*0.55f + shiftPx, size.height*0.6f))
    }
}

private fun DrawScope.drawCloudyAnimated(cloudShift: Float) {
    val shiftPx = (cloudShift - 0.5f) * size.width * 0.35f
    drawCloud(offset = Offset(size.width*0.5f + shiftPx, size.height*0.55f))
}

private fun DrawScope.drawRainAnimated(fallShift: Float, cloudShift: Float) {
    val shiftPx = (cloudShift - 0.5f) * size.width * 0.25f
    val cloudY = size.height*0.48f
    drawCloud(offset = Offset(size.width*0.5f + shiftPx, cloudY))
    // raindrops falling
    val columns = 8
    val baseX = size.width*0.25f
    val span = size.width*0.5f
    for (i in 0 until columns) {
        val x = baseX + (i.toFloat()/(columns-1)) * span
        val yStart = size.height*0.6f
        val length = size.height*0.22f
        val yOffset = (fallShift * (length + 40f)) % (length + 40f)
        drawLine(
            color = Color(0xAA90CAF9),
            start = Offset(x, yStart + yOffset - 40f),
            end = Offset(x - 12f, yStart + yOffset + 12f),
            strokeWidth = 6f
        )
    }
}

private fun DrawScope.drawSnowAnimated(fallShift: Float, cloudShift: Float) {
    val shiftPx = (cloudShift - 0.5f) * size.width * 0.25f
    drawCloud(offset = Offset(size.width*0.5f + shiftPx, size.height*0.5f))
    val flakes = 10
    val baseX = size.width*0.28f
    val span = size.width*0.54f
    for (i in 0 until flakes) {
        val x = baseX + (i.toFloat()/(flakes-1)) * span
        val yStart = size.height*0.62f
        val fall = (fallShift * size.height*0.25f + i*8) % (size.height*0.25f)
        val wiggle = sin((fallShift*2*PI + i) ).toFloat() * 8f
        drawCircle(Color(0xCCFFFFFF), radius = 6f, center = Offset(x + wiggle, yStart + fall))
    }
}

private fun DrawScope.drawThunderAnimated(alpha: Float, cloudShift: Float) {
    val shiftPx = (cloudShift - 0.5f) * size.width * 0.2f
    drawCloud(offset = Offset(size.width*0.5f + shiftPx, size.height*0.5f))
    val p = Path().apply {
        moveTo(size.width*0.6f, size.height*0.65f)
        lineTo(size.width*0.55f, size.height*0.8f)
        lineTo(size.width*0.65f, size.height*0.8f)
        lineTo(size.width*0.6f, size.height*0.95f)
        lineTo(size.width*0.72f, size.height*0.78f)
        lineTo(size.width*0.62f, size.height*0.78f)
        close()
    }
    drawPath(p, Color(0xFFFFEE58).copy(alpha = alpha.coerceIn(0f, 1f)))
}

private fun DrawScope.drawCloud(offset: Offset) {
    val w = size.width
    val h = size.height
    val baseY = offset.y
    val baseX = offset.x
    drawCircle(Color(0xAAFFFFFF), radius = w*0.18f, center = Offset(baseX - w*0.18f, baseY))
    drawCircle(Color(0xCCFFFFFF), radius = w*0.22f, center = Offset(baseX, baseY - h*0.05f))
    drawCircle(Color(0xB3FFFFFF), radius = w*0.16f, center = Offset(baseX + w*0.18f, baseY + h*0.02f))
    drawRoundRect(
        color = Color(0xB3FFFFFF),
        topLeft = Offset(baseX - w*0.28f, baseY - h*0.02f),
        size = Size(w*0.56f, h*0.16f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w*0.08f, h*0.08f)
    )
}
