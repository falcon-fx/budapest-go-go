package com.example.myapplication

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex
import dagger.hilt.android.HiltAndroidApp
import org.conscrypt.Conscrypt
import java.security.Security

@HiltAndroidApp
class BkkApp: Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
    }
}