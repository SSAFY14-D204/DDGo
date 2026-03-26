package com.ddgo.app.core.ui.components

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

private object SystemBarManager {
    private var activeCount = 0

    fun acquire(activity: ComponentActivity?) {
        if (activeCount == 0) {
            activity?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
                navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK)
            )
        }
        activeCount++
    }

    fun release(activity: ComponentActivity?) {
        activeCount--
        if (activeCount <= 0) {
            activeCount = 0
            activity?.enableEdgeToEdge()
        }
    }
}

@Composable
fun DarkSystemBarsEffect() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val activity = context.findActivity()
        SystemBarManager.acquire(activity)
        
        onDispose {
            SystemBarManager.release(activity)
        }
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
