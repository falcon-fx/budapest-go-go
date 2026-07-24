package com.falconfx.gtfsviewer

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import dagger.hilt.android.HiltAndroidApp
import org.conscrypt.Conscrypt
import org.osmdroid.config.Configuration
import java.io.File
import java.security.Security

@HiltAndroidApp
class BkkApp: Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
    }

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().apply {
            userAgentValue = packageName
            cacheMapTileCount = 12.toShort()
            cacheMapTileOvershoot = 4.toShort()
            osmdroidTileCache = File(cacheDir, "osmdroid")
        }
    }
}