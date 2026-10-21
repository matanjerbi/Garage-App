package com.matan.garage

import android.app.Application
import androidx.room.Room
import com.matan.garage.data.AppDatabase
import com.matan.garage.data.Repo
import com.matan.garage.data.SettingsStore
import com.matan.garage.notify.Reminders

class GarageApp : Application() {
    lateinit var repo: Repo
        private set
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "garage.db").build()
        repo = Repo(this, db)
        settings = SettingsStore(this)
        Reminders.createChannel(this)
        Reminders.schedule(this, settings.flow.value, replace = false)
    }
}
