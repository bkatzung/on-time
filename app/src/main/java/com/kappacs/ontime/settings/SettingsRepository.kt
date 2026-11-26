package com.kappacs.ontime.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val deContext = context.applicationContext.createDeviceProtectedStorageContext()

    companion object {
        val KEY_BG_SOUND_URI = stringPreferencesKey("bg_sound_uri")
        val KEY_ALARM_SOUND_URI = stringPreferencesKey("alarm_sound_uri")
        val KEY_BG_FADE_TIME_SEC = intPreferencesKey("bg_fade_time_sec")
        val KEY_ALARM_SNOOZE_MIN = intPreferencesKey("alarm_snooze_min")
        val KEY_HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        val KEY_BG_PLAYING = booleanPreferencesKey("bg_playing")
        val KEY_BG_DURATION_MS = longPreferencesKey("bg_duration_ms")
        val KEY_BG_END_TIME = longPreferencesKey("bg_end_time")
        val KEY_ALARM_RINGING = booleanPreferencesKey("alarm_ringing")
        val KEY_ALARM_SNOOZED = booleanPreferencesKey("alarm_snoozed")
        val KEY_ALARM_SNOOZE_TRIGGER_TIME = longPreferencesKey("alarm_snooze_trigger_time")
    }

    val bgSoundUri: Flow<String?> = deContext.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_BG_SOUND_URI]
        }

    val alarmSoundUri: Flow<String?> = deContext.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_ALARM_SOUND_URI]
        }

    val bgFadeTimeSec: Flow<Int> = deContext.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_BG_FADE_TIME_SEC] ?: 0
        }

    val alarmSnoozeMin: Flow<Int> = deContext.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_ALARM_SNOOZE_MIN] ?: 5
        }

    val hapticFeedbackEnabled: Flow<Boolean> = deContext.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_HAPTIC_FEEDBACK_ENABLED] ?: false
        }

    val bgPlaying: Flow<Boolean> = deContext.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_BG_PLAYING] ?: false
        }

    val bgDurationMs: Flow<Long> = deContext.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_BG_DURATION_MS] ?: 0L
        }

    val bgEndTime: Flow<Long> = deContext.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_BG_END_TIME] ?: 0L
        }

    suspend fun setBgPlayingState(playing: Boolean, durationMs: Long = 0L, endTime: Long = 0L) {
        deContext.dataStore.edit { preferences ->
            preferences[KEY_BG_PLAYING] = playing
            preferences[KEY_BG_DURATION_MS] = durationMs
            preferences[KEY_BG_END_TIME] = endTime
        }
    }

    val alarmRinging: Flow<Boolean> = deContext.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_ALARM_RINGING] ?: false
        }

    val alarmSnoozed: Flow<Boolean> = deContext.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_ALARM_SNOOZED] ?: false
        }

    val alarmSnoozeTriggerTime: Flow<Long> = deContext.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_ALARM_SNOOZE_TRIGGER_TIME] ?: 0L
        }

    suspend fun setAlarmRingingState(ringing: Boolean) {
        deContext.dataStore.edit { preferences ->
            preferences[KEY_ALARM_RINGING] = ringing
            if (ringing) {
                preferences[KEY_ALARM_SNOOZED] = false
            }
        }
    }

    suspend fun setAlarmSnoozedState(snoozed: Boolean, triggerTime: Long = 0L) {
        deContext.dataStore.edit { preferences ->
            preferences[KEY_ALARM_SNOOZED] = snoozed
            preferences[KEY_ALARM_SNOOZE_TRIGGER_TIME] = triggerTime
            if (snoozed) {
                preferences[KEY_ALARM_RINGING] = false
            }
        }
    }

    suspend fun setBgSoundUri(uri: String?) {
        deContext.dataStore.edit { preferences ->
            if (uri != null) {
                preferences[KEY_BG_SOUND_URI] = uri
            } else {
                preferences.remove(KEY_BG_SOUND_URI)
            }
        }
    }

    suspend fun setAlarmSoundUri(uri: String?) {
        deContext.dataStore.edit { preferences ->
            if (uri != null) {
                preferences[KEY_ALARM_SOUND_URI] = uri
            } else {
                preferences.remove(KEY_ALARM_SOUND_URI)
            }
        }
    }

    suspend fun setBgFadeTimeSec(seconds: Int) {
        deContext.dataStore.edit { preferences ->
            preferences[KEY_BG_FADE_TIME_SEC] = seconds
        }
    }

    suspend fun setAlarmSnoozeMin(minutes: Int) {
        deContext.dataStore.edit { preferences ->
            preferences[KEY_ALARM_SNOOZE_MIN] = minutes
        }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        deContext.dataStore.edit { preferences ->
            preferences[KEY_HAPTIC_FEEDBACK_ENABLED] = enabled
        }
    }
}
