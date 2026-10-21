package com.suibian.bulletchat

import android.app.Application
import com.suibian.bulletchat.data.SettingsStore
import com.suibian.bulletchat.data.StatsStore
import com.suibian.bulletchat.update.UpdateChecker

class BulletChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        StatsStore.init(this)
        UpdateChecker.init(this)
    }
}
