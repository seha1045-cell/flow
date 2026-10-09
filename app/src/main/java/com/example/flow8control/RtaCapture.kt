package com.example.flow8control

import android.content.Context
import android.media.*
import android.media.audiofx.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

object RtaCapture {
    val spectrum = MutableStateFlow(FloatArray(1024) { -100f })
    val running = MutableStateFlow(false)

    private var job: Job? = null

    fun start(context: Context, scope: CoroutineScope) {
        if (job?.isActive == true) return

        job = scope.launch(Dispatchers.IO) {
            var recorder: AudioRecord? = null
            try {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val usb = audio.getDevices(AudioManager.GET_DEVICES_INPUTS)
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
                    ?: return@launch

                val rate = 48000
                val minBytes = AudioRecord.getMinBufferSize(
                    rate, AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBytes <= 0) return@launch

                recorder = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBytes, 8192))
                    .build()

                if (recorder.state != AudioRecord.STATE_INITIALIZED) return@launch
                recorder.setPreferredDevice(usb)
                recorder.startRecording()
                running.value = true

                val buffer = ShortArray(4096)
                while (isActive) {
                    val n = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (n >= 4096) {
                        val mono = ShortArray(2048) { i ->
                            ((buffer[i * 2].toInt() + buffer[i * 2 + 1].toInt()) / 2).toShort()
                        }
                        spectrum.value = FftAnalyzer.analyze(mono, rate)
                    }
                }
            } finally {
                running.value = false
                try { recorder?.stop() } catch (_: Exception) {}
                recorder?.release()
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}
