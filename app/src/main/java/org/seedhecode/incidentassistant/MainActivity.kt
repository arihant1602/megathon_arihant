package org.seedhecode.incidentassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import org.seedhecode.incidentassistant.ui.session.AppSessionViewModel
import org.seedhecode.incidentassistant.feature.report.CaptureState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.seedhecode.incidentassistant.feature.report.ReportViewModel
import org.seedhecode.incidentassistant.ui.navigation.SentinelNavGraph
import org.seedhecode.incidentassistant.ui.theme.IncidentAssistantTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ReportViewModel by viewModels()
    private val sessionViewModel: AppSessionViewModel by viewModels()

    var permissionRationale by mutableStateOf(false)
        private set
    var settingsRequired by mutableStateOf(false)
        private set

    private var foreground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
        )
        setContent {
            IncidentAssistantTheme {
                SentinelNavGraph(
                    viewModel = viewModel,
                    sessionViewModel = sessionViewModel,
                    onRequestPermission = { requestCapture() },
                    permissionRationale = permissionRationale,
                    settingsRequired = settingsRequired,
                    onDismissDialog = {
                        permissionRationale = false
                        settingsRequired = false
                    },
                    onOpenSettings = {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        viewModel.refreshLibrary()
    }

    private fun requestCapture() {
        if (!foreground || !sessionViewModel.captureScreenVisible ||
            viewModel.uiState.value.captureState != CaptureState.IDLE) return
        if (permissionRationale) {
            permissionRationale = false
            askPermission()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.startCapture()
            return
        }
        val preferences = getPreferences(MODE_PRIVATE)
        val askedBefore = preferences.getBoolean("microphone_permission_requested", false)
        if (askedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            settingsRequired = true
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            permissionRationale = true
        } else {
            askPermission()
        }
    }

    private fun askPermission() {
        getPreferences(MODE_PRIVATE).edit().putBoolean("microphone_permission_requested", true).apply()
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_PERMISSION)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != MICROPHONE_PERMISSION) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && foreground &&
            sessionViewModel.captureScreenVisible && viewModel.uiState.value.captureState == CaptureState.IDLE) {
            viewModel.startCapture()
        } else {
            // Permission denied
        }
    }

    override fun onStop() {
        foreground = false
        viewModel.stopPlayback()
        if (viewModel.uiState.value.captureState != CaptureState.SAVING) viewModel.forceStopCapture()
        super.onStop()
    }

    companion object {
        private const val MICROPHONE_PERMISSION = 100
    }
}
