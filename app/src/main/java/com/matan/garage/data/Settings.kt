package com.matan.garage.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AppSettings(
    val notifHour: Int = 19,
    val notifMinute: Int = 0,
    val soonKm: Int = 500,
    val soonDays: Int = 14,
    val docLeadDays: Int = 30,
    val odoReminderDays: Int = 10,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val flow: StateFlow<AppSettings> = state

    private fun load() = AppSettings(
        notifHour = prefs.getInt("notifHour", 19),
        notifMinute = prefs.getInt("notifMinute", 0),
        soonKm = prefs.getInt("soonKm", 500),
        soonDays = prefs.getInt("soonDays", 14),
        docLeadDays = prefs.getInt("docLeadDays", 30),
        odoReminderDays = prefs.getInt("odoReminderDays", 10),
    )

    fun update(s: AppSettings) {
        prefs.edit()
            .putInt("notifHour", s.notifHour)
            .putInt("notifMinute", s.notifMinute)
            .putInt("soonKm", s.soonKm)
            .putInt("soonDays", s.soonDays)
            .putInt("docLeadDays", s.docLeadDays)
            .putInt("odoReminderDays", s.odoReminderDays)
            .apply()
        state.value = s
    }
}
