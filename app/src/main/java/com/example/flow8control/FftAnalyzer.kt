package com.example.flow8control

import kotlin.math.*

object FftAnalyzer {
    fun analyze(samples: ShortArray, sampleRate: Int = 48000): FloatArray {
        val n = 2048
        val real = DoubleArray(n)
        val imag = DoubleArray(n)

        for (i in 0 until n) {
            val sample = if (i < samples.size) samples[i] / 32768.0 else 0.0
            val window = 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
            real[i] = sample * window
        }

        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val temp = real[i]
                real[i] = real[j]
                real[j] = temp
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wLenR = cos(angle)
            val wLenI = sin(angle)

            var start = 0
            while (start < n) {
                var wr = 1.0
                var wi = 0.0

                for (k in 0 until len / 2) {
                    val a = start + k
                    val b = a + len / 2

                    val tr = wr * real[b] - wi * imag[b]
                    val ti = wr * imag[b] + wi * real[b]

                    real[b] = real[a] - tr
                    imag[b] = imag[a] - ti
                    real[a] += tr
                    imag[a] += ti

                    val nextR = wr * wLenR - wi * wLenI
                    wi = wr * wLenI + wi * wLenR
                    wr = nextR
                }
                start += len
            }
            len *= 2
        }

        return FloatArray(n / 2) { i ->
            val magnitude = hypot(real[i], imag[i]) * 2.0 / (n * 0.5)
            (20.0 * log10(max(magnitude, 1e-9))).toFloat()
        }
    }
}
