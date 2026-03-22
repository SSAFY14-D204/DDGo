package com.ddgo.wear.runtime

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class WatchHaptics(
    context: Context
) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.applicationContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun triggerAlert() {
        val deviceVibrator = vibrator ?: return
        if (!deviceVibrator.hasVibrator()) {
            return
        }
        val timings = longArrayOf(0L, 180L, 80L, 220L)
        val amplitudes = intArrayOf(0, 180, 0, 255)
        deviceVibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }
}
