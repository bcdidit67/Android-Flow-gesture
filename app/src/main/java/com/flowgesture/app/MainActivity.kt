package com.flowgesture.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.flowgesture.app.data.SettingsRepository
import com.flowgesture.app.ui.FlowGestureApp
import com.flowgesture.app.ui.theme.FlowGestureTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()

        setContent {
            val context = LocalContext.current
            val repository = remember { SettingsRepository.get(context) }
            val dynamicColor by repository.dynamicColorFlow.collectAsState(initial = true)

            FlowGestureTheme(dynamicColor = dynamicColor) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    FlowGestureApp()
                }
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}