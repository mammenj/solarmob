package com.mobile.solartracker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

fun isStorageManagerGranted(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }
}

fun openManageStorageSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val packageUri = Uri.parse("package:${context.packageName}")
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION, packageUri),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
        )
        for (intent in intents) {
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                // try next
            }
        }
    }
}

@Composable
fun StoragePermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(isStorageManagerGranted()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = isStorageManagerGranted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (granted) {
        content()
    } else {
        StoragePermissionScreen(onOpenSettings = { openManageStorageSettings(context) })
    }
}

@Composable
private fun StoragePermissionScreen(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Storage access required",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Solar Tracker stores its databases in Documents/solarmob so they are " +
                "accessible from other apps and file managers. To do this it needs " +
                "\"All files access\" permission.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpenSettings) {
            Text("Grant access in Settings")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "After granting, return to this app and it will continue automatically.",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}
