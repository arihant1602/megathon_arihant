package org.seedhecode.incidentassistant

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import org.seedhecode.incidentassistant.feature.assistant.IncidentAssistantViewModel
import org.seedhecode.incidentassistant.feature.assistant.Phase
import org.seedhecode.incidentassistant.ui.app.SentinelApp
import org.seedhecode.incidentassistant.ui.theme.IncidentAssistantTheme

class MainActivity : ComponentActivity() {
    private val assistant: IncidentAssistantViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContent {
            IncidentAssistantTheme {
                SentinelApp(assistant) {
                    if (assistant.state.value.phase == Phase.LISTENING) assistant.stopListening() else talk()
                }
            }
        }
    }

    private fun talk() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) assistant.startListening()
        else requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_PERMISSION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != MICROPHONE_PERMISSION) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) assistant.startListening()
        else Toast.makeText(this, "Microphone permission is needed to talk. You can still type.", Toast.LENGTH_LONG).show()
    }

    override fun onStop() {
        assistant.pause()
        super.onStop()
    }

    companion object {
        private const val MICROPHONE_PERMISSION = 100
    }
}
