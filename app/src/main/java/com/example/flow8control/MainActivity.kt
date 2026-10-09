package com.example.flow8control

import android.content.Context
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UsbDiagnostic(this) }
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
                val iface = d.getInterface(i)
                appendLine("  Interface $i: class=${iface.interfaceClass}, subclass=${iface.interfaceSubclass}, protocol=${iface.interfaceProtocol}, endpoints=${iface.endpointCount}")
            }
        }
    } catch (e: Exception) { appendLine("USB enumeration error: ${e.javaClass.simpleName}: ${e.message}") }
    appendLine()
    appendLine("ANDROID USB MIDI")
    try {
        val midi = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
        val devices = midi.devices.filter { it.type == MidiDeviceInfo.TYPE_USB }
        if (devices.isEmpty()) appendLine("No USB MIDI devices exposed by Android")
        devices.forEach { d ->
            appendLine("• ${d.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "USB MIDI"} | id=${d.id} | input ports=${d.inputPortCount} | output ports=${d.outputPortCount}")
        }
    } catch (e: Exception) { appendLine("MIDI enumeration error: ${e.javaClass.simpleName}: ${e.message}") }
    appendLine()
    appendLine("ANDROID AUDIO ROUTES")
    try {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val routes = audio.getDevices(AudioManager.GET_DEVICES_ALL)
        val usbRoutes = routes.filter { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY }
        if (usbRoutes.isEmpty()) appendLine("No USB audio route reported")
        usbRoutes.forEach { d -> appendLine("• ${d.productName} | type=${d.type} | input=${d.isSource} | output=${d.isSink} | sample rates=${d.sampleRates.joinToString()}") }
    } catch (e: Exception) { appendLine("Audio enumeration error: ${e.javaClass.simpleName}: ${e.message}") }
    appendLine()
    appendLine("READ-ONLY DIAGNOSTIC: No MIDI commands, no audio recording, no mixer settings changed.")
}

@Composable
private fun UsbDiagnostic(context: Context) {
    var report by remember { mutableStateOf("Tap SCAN USB while FLOW 8 is connected to the tablet.") }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7DCBFF))) {
        Column(Modifier.fillMaxSize().background(Color(0xFF101923)).padding(16.dp)) {
            Text("FLOW 8 USB DIAGNOSTIC", style = MaterialTheme.typography.headlineSmall)
            Text("Beta 0.3 • Galaxy Tab S7 • USB only", color = Color.LightGray)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { report = inspect(context) }) { Text("SCAN USB / MIDI / AUDIO") }
            Spacer(Modifier.height(12.dp))
            Text("No Bluetooth. No mixer control commands.", color = Color(0xFFFFC777))
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(report, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
