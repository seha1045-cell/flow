package com.example.flow8control

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.log10

@Composable
fun RtaGraph(spectrum: FloatArray, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(240.dp)) {
        val left = 20f
        val right = size.width - 12f
        val top = 12f
        val bottom = size.height - 20f

        // 주파수 축: 20 Hz ~ 20 kHz (로그 스케일)
        val minFreq = 20.0
        val maxFreq = 20000.0
        val sampleRate = 48000.0
        val fftSize = 2048

        val path = Path()
        var started = false

        spectrum.forEachIndexed { index, db ->
            val frequency = index * sampleRate / fftSize
            if (frequency in minFreq..maxFreq) {
                val x = left + (
                    log10(frequency / minFreq) /
                    log10(maxFreq / minFreq)
                ).toFloat() * (right - left)

                val y = bottom - (
                    ((db + 100f) / 100f).coerceIn(0f, 1f)
                ) * (bottom - top)

                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
        }

        drawPath(
            path = path,
            color = Color.Cyan,
            style = Stroke(width = 2.5f)
        )
    }
}
