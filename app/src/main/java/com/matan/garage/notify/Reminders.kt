package com.matan.garage.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.matan.garage.GarageApp
import com.matan.garage.MainActivity
import com.matan.garage.R
import com.matan.garage.data.AppSettings
import com.matan.garage.logic.Calc
import com.matan.garage.logic.Snapshot
import com.matan.garage.logic.Status
import com.matan.garage.logic.Thresholds
import com.matan.garage.ui.KM
import com.matan.garage.ui.label
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object Reminders {
    private const val CHANNEL = "reminders"
    private const val WORK = "daily-reminders"
    const val EXTRA_VEHICLE = "vehicleId"

    fun createChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL, "תזכורות טיפולים", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    /** Daily check at the configured time. [replace] = true after the user changes the time. */
    fun schedule(context: Context, s: AppSettings, replace: Boolean) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(s.notifHour, s.notifMinute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        val policy = if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, policy, req)
    }

    /** Runs the check right now (from settings, to test notifications). */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().build())
    }

    fun check(context: Context, snap: Snapshot, s: AppSettings, today: LocalDate = LocalDate.now()) {
        val prefs = context.getSharedPreferences("notif_state", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        val todayDay = today.toEpochDay()
        val th = Thresholds(s.soonKm, s.soonDays)

        for (v in snap.vehicles.filter { !it.archived }) {
            val lines = mutableListOf<String>()

            for (st in Calc.vehicleItemStates(snap, v.id, today, th)) {
                val sig = "${st.lastKm}_${st.lastDate.toEpochDay()}" // changes when the item is done again
                when (st.status) {
                    Status.OVERDUE -> {
                        val key = "over_${st.item.id}"
                        val prev = prefs.getString(key, null)?.split("|")
                        val send = prev == null || prev.size != 2 || prev[0] != sig ||
                            todayDay - (prev[1].toLongOrNull() ?: 0L) >= 3
                        if (send) {
                            lines += "באיחור: ${st.item.name}"
                            edit.putString(key, "$sig|$todayDay")
                        }
                    }
                    Status.DUE_SOON -> {
                        val key = "soon_${st.item.id}"
                        if (prefs.getString(key, null) != sig) {
                            val parts = mutableListOf<String>()
                            st.kmLeft?.let { parts += "עוד $it $KM" }
                            st.daysLeft?.let { parts += "עוד $it ימים" }
                            lines += "מתקרב: ${st.item.name} (${parts.joinToString(", ")})"
                            edit.putString(key, sig)
                        }
                    }
                    Status.OK -> {}
                }
            }

            for (d in snap.documentsOf(v.id)) {
                val left = d.expiryDate - todayDay
                if (left == 0L && prefs.getLong("docday_${d.id}", -1) != d.expiryDate) {
                    lines += "${d.type.label()} פג היום"
                    edit.putLong("docday_${d.id}", d.expiryDate)
                } else if (left in 1..s.docLeadDays.toLong() && prefs.getLong("doclead_${d.id}", -1) != d.expiryDate) {
                    lines += "${d.type.label()} פג בעוד $left ימים"
                    edit.putLong("doclead_${d.id}", d.expiryDate)
                }
            }

            val lastReading = Calc.lastReadingDate(snap.readingsOf(v.id))
            if (lastReading != null && s.odoReminderDays > 0) {
                val since = todayDay - lastReading.toEpochDay()
                val lastSent = prefs.getLong("odo_${v.id}", -1)
                if (since >= s.odoReminderDays && (lastSent < 0 || todayDay - lastSent >= s.odoReminderDays)) {
                    lines += "לא עודכן $KM כבר $since ימים"
                    edit.putLong("odo_${v.id}", todayDay)
                }
            }

            if (lines.isNotEmpty()) notify(context, v.id, v.name, lines)
        }
        edit.apply()
    }

    private fun notify(context: Context, vehicleId: Long, title: String, lines: List<String>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_VEHICLE, vehicleId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            context, vehicleId.toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine(it) }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(if (lines.size == 1) lines[0] else "${lines.size} תזכורות")
            .setStyle(style)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(vehicleId.toInt(), n)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GarageApp
        val snap = app.repo.snapshot().first()
        Reminders.check(applicationContext, snap, app.settings.flow.value)
        return Result.success()
    }
}
