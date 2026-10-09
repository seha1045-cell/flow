package com.example.flow8control

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.media.*
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.math.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Diagnostic(this) }
    }
}

private fun inspect(context: Context): String = buildString {
    appendLine("USB HOST DEVICES")
    try {
        val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = usb.deviceList.values.sortedBy { it.deviceName }
        if (devices.isEmpty()) appendLine("No USB devices visible")
        devices.forEach { d ->
            appendLine("• ${d.productName ?: "Unknown"} / ${d.manufacturerName ?: "Unknown"}")
            appendLine("  VID:PID = %04X:%04X | USB permission: %s".format(d.vendorId, d.productId, usb.hasPermission(d)))
            appendLine("  Device class=${d.deviceClass} interfaces=${d.interfaceCount}")
            for (i in 0 until d.interfaceCount) {
                val f = d.getInterface(i)
                appendLine("  Interface $i: class=${f.interfaceClass}, subclass=${f.interfaceSubclass}, protocol=${f.interfaceProtocol}, endpoints=${f.endpointCount}")
            }
        }
    } catch (e: Exception) { appendLine("USB error: ${e.message}") }
    appendLine("\nANDROID USB MIDI")
    try {
        val midi = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
        val devices = midi.devices.filter { it.type == MidiDeviceInfo.TYPE_USB }
        if (devices.isEmpty()) appendLine("No USB MIDI devices")
        devices.forEach { d -> appendLine("• ${d.properties.getString(MidiDeviceInfo.PROPERTY_NAME)} | id=${d.id} | input ports=${d.inputPortCount} | output ports=${d.outputPortCount}") }
    } catch (e: Exception) { appendLine("MIDI error: ${e.message}") }
    appendLine("\nANDROID AUDIO ROUTES / CHANNEL CAPABILITIES")
    try {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val routes = audio.getDevices(AudioManager.GET_DEVICES_ALL).filter {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
        if (routes.isEmpty()) appendLine("No USB audio route")
        routes.forEach { d ->
            appendLine("• ${d.productName} | id=${d.id} | input=${d.isSource} | output=${d.isSink}")
            appendLine("  sample rates=${d.sampleRates.joinToString().ifEmpty { "not specified" }}")
            appendLine("  channel counts=${d.channelCounts.joinToString().ifEmpty { "not specified" }}")
            appendLine("  channel masks=${d.channelMasks.joinToString().ifEmpty { "not specified" }}")
            appendLine("  encodings=${d.encodings.joinToString().ifEmpty { "not specified" }}")
        }
    } catch (e: Exception) { appendLine("Audio error: ${e.message}") }
    appendLine("\nNo MIDI commands sent. No mixer settings changed.")
}

private fun capture(context: Context): String {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return "Microphone permission required. Allow permission and retry."
    val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val usb = manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
        ?: return "No USB audio INPUT device."
    val rate = 48000
    val channelMask = AudioFormat.CHANNEL_IN_STEREO
    val minBytes = AudioRecord.getMinBufferSize(rate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
    if (minBytes <= 0) return "Stereo 48kHz unsupported: $minBytes"
    var recorder: AudioRecord? = null
    return try {
        recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(channelMask).build())
            .setBufferSizeInBytes(max(minBytes, 8192))
            .build()
        val r = recorder!!
        if (r.state != AudioRecord.STATE_INITIALIZED) return "AudioRecord initialization failed"
        val preferred = r.setPreferredDevice(usb)
        r.startRecording()
        val routed = r.routedDevice
        val data = ShortArray(4096)
        var total = 0L
        var sum = 0.0
        var peak = 0
        repeat(12) {
            val n = r.read(data, 0, data.size, AudioRecord.READ_BLOCKING)
            if (n > 0) {
                total += n
                for (i in 0 until n) { val v = data[i].toInt(); sum += v.toDouble() * v; peak = max(peak, abs(v)) }
            }
        }
        val rms = if (total > 0) sqrt(sum / total) / 32768.0 else 0.0
        "USB input: ${usb.productName}\nPreferred routing accepted: $preferred\nActual routed device: ${routed?.productName ?: "none"} (id=${routed?.id ?: -1})\nPCM samples read: $total\nRMS: ${"%.1f".format(20 * log10(max(rms, 1e-9)))} dBFS\nPeak: ${"%.1f".format(20 * log10(max(peak / 32768.0, 1e-9)))} dBFS\nThis is a stereo capture test, NOT a multichannel/RTA verification."
    } catch (e: Exception) { "Audio capture error: ${e.javaClass.simpleName}: ${e.message}" }
    finally { try { recorder?.stop() } catch (_: Exception) {}; recorder?.release() }
}

@Composable
private fun Diagnostic(context: MainActivity) {
    var report by remember { mutableStateOf("Connect FLOW 8, then scan in both Streaming and Recording modes.") }
    var captureReport by remember { mutableStateOf("Audio test is optional. It records briefly for measurement only; nothing is saved.") }
    var busy by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7DCBFF))) {
        Column(Modifier.fillMaxSize().background(Color(0xFF101923)).padding(16.dp)) {
            Text("FLOW 8 USB DIAGNOSTIC", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text("Beta 0.4 • USB only • Mode comparison", color = Color.LightGray)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { report = inspect(context) }) { Text("SCAN USB / MIDI / AUDIO") }
            Spacer(Modifier.height(8.dp))
            Button(enabled = !busy, onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    context.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 42)
                    captureReport = "Permission requested. After allowing, tap TEST again."
                } else {
                    busy = true
                    captureReport = "Testing USB audio..."
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        val result = capture(context)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { captureReport = result; busy = false }
                    }
                }
            }) { Text("TEST USB AUDIO INPUT (STEREO)") }
            Text("No Bluetooth. No MIDI commands. No mixer changes.", color = Color(0xFFFFC777))
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(captureReport, color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(report, color = Color.White)
            }
        }
    }
}
