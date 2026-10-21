package com.example.rewardstracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.rewardstracker.ui.HomeScreen
import com.example.rewardstracker.ui.LoginScreen
import com.example.rewardstracker.work.Scheduler

class MainActivity : ComponentActivity() {
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Scheduler.ensureScheduled(this)

        setContent { MaterialTheme { App() } }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    var showLogin by remember { mutableStateOf(false) }

    if (showLogin) {
        LoginScreen(onDone = {
            showLogin = false
            // Fetch once right after login so the first data point exists.
            Scheduler.runNow(context)
        })
    } else {
        HomeScreen(onLogin = { showLogin = true })
    }
}
