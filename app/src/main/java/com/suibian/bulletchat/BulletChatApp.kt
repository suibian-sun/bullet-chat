package com.suibian.bulletchat

import android.app.Application
import com.suibian.bulletchat.data.SettingsStore

class BulletChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
    }
}
