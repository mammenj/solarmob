package com.mobile.solartracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mobile.solartracker.ui.SolarTrackerRoot
import com.mobile.solartracker.ui.StoragePermissionGate
import com.mobile.solartracker.ui.theme.SolarTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SolarTrackerTheme {
                StoragePermissionGate {
                    SolarTrackerRoot()
                }
            }
        }
    }
}
