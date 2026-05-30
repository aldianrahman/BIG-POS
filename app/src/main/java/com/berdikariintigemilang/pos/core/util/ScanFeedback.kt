package com.berdikariintigemilang.pos.core.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

/** Umpan balik scan sukses: bunyi beep + getar (keduanya aman bila gagal). */
fun scanFeedback(context: Context) {
    beep()
    vibrate(context)
}

private fun beep() {
    try {
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
        // Lepaskan resource setelah tone selesai.
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 250)
    } catch (_: Exception) {
    }
}

private fun vibrate(context: Context) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (_: Exception) {
    }
}
