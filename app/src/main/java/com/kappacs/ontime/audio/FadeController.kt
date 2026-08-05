package com.kappacs.ontime.audio

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.*

/**
 * FadeController handles smooth volume transitions (fade-in, fade-out, and sequential transitions)
 * for MediaPlayers using Kotlin Coroutines.
 *
 * Why Coroutines?
 * Coroutines allow us to perform asynchronous, non-blocking delays (using `delay()`) on the main thread
 * or a background thread without blocking the UI or service execution. They are also easily cancellable,
 * which is crucial if a user stops playback or triggers a new action while a fade is in progress.
 *
 * Why a Quadratic Curve?
 * Human perception of loudness is logarithmic, not linear. If we fade volume linearly (e.g., 1.0, 0.9, 0.8...),
 * the sound seems to drop very quickly at first and then linger at a low volume. By using a quadratic curve
 * (volume = progress * progress), the volume changes more gradually at the lower end, resulting in a much
 * smoother and more natural-sounding transition to the human ear.
 */
class FadeController(private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "FadeController"
        private const val FADE_INTERVAL_MS = 50L // Update volume every 50ms for smooth transitions
    }

    private var bgFadeJob: Job? = null
    private var alarmFadeJob: Job? = null
    private var transitionJob: Job? = null

    /**
     * Fades out a MediaPlayer's volume to 0 over the specified duration, then invokes [onComplete].
     */
    fun fadeOut(player: MediaPlayer?, durationMs: Long, onComplete: () -> Unit) {
        cancelBgFade()
        if (player == null) {
            onComplete()
            return
        }
        if (durationMs <= 0) {
            try {
                player.setVolume(0f, 0f)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting volume to 0", e)
            }
            onComplete()
            return
        }

        bgFadeJob = scope.launch {
            val steps = durationMs / FADE_INTERVAL_MS
            for (i in 0..steps) {
                if (!isActive) break
                val progress = 1.0f - (i.toFloat() / steps)
                val volume = progress * progress // Quadratic curve
                try {
                    player.setVolume(volume, volume)
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting volume during fade out", e)
                    break
                }
                delay(FADE_INTERVAL_MS)
            }
            try {
                player.setVolume(0f, 0f)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting final volume to 0", e)
            }
            onComplete()
        }
    }

    /**
     * Fades in a MediaPlayer's volume from 0 to 1 over the specified duration.
     */
    fun fadeIn(player: MediaPlayer?, durationMs: Long, onComplete: (() -> Unit)? = null) {
        cancelAlarmFade()
        if (player == null) {
            onComplete?.invoke()
            return
        }
        if (durationMs <= 0) {
            try {
                player.setVolume(1f, 1f)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting volume to 1", e)
            }
            onComplete?.invoke()
            return
        }

        alarmFadeJob = scope.launch {
            val steps = durationMs / FADE_INTERVAL_MS
            try {
                player.setVolume(0f, 0f)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting initial volume to 0", e)
            }
            for (i in 0..steps) {
                if (!isActive) break
                val progress = i.toFloat() / steps
                val volume = progress * progress // Quadratic curve
                try {
                    player.setVolume(volume, volume)
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting volume during fade in", e)
                    break
                }
                delay(FADE_INTERVAL_MS)
            }
            try {
                player.setVolume(1f, 1f)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting final volume to 1", e)
            }
            onComplete?.invoke()
        }
    }

    /**
     * Performs a sequential transition:
     * 1. Fades out the background player to silent over [transitionTimeMs].
     * 2. Invokes [onBgSilent] (allowing the service to stop/release the background player).
     * 3. Invokes [onFadeInAlarm] with the transition time, allowing the service to start the alarm player
     *    and fade it in.
     */
    fun transitionBgToAlarm(
        bgPlayer: MediaPlayer?,
        transitionTimeMs: Long,
        onBgSilent: () -> Unit,
        onFadeInAlarm: (Long) -> Unit
    ) {
        cancelAll()
        transitionJob = scope.launch {
            // 1. Fade out background sound
            if (bgPlayer != null && transitionTimeMs > 0) {
                val steps = transitionTimeMs / FADE_INTERVAL_MS
                for (i in 0..steps) {
                    if (!isActive) break
                    val progress = 1.0f - (i.toFloat() / steps)
                    val volume = progress * progress
                    try {
                        bgPlayer.setVolume(volume, volume)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting bg volume during transition", e)
                        break
                    }
                    delay(FADE_INTERVAL_MS)
                }
                try {
                    bgPlayer.setVolume(0f, 0f)
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting final bg volume to 0", e)
                }
            }

            // Notify that background is silent (so service can stop/release it)
            onBgSilent()

            // 2. Trigger alarm fade-in
            onFadeInAlarm(transitionTimeMs)
        }
    }

    fun cancelBgFade() {
        bgFadeJob?.cancel()
        bgFadeJob = null
    }

    fun cancelAlarmFade() {
        alarmFadeJob?.cancel()
        alarmFadeJob = null
    }

    fun cancelTransition() {
        transitionJob?.cancel()
        transitionJob = null
    }

    fun cancelAll() {
        cancelBgFade()
        cancelAlarmFade()
        cancelTransition()
    }
}
