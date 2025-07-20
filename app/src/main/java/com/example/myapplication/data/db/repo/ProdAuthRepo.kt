package com.example.myapplication.data.db.repo

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ProdAuthRepo @Inject constructor(
    @ApplicationContext private val context: Context
): AuthRepo {

    private val preferences = context.getSharedPreferences("gogo_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_API = "api_key"
    }

    override fun saveApiKey(key: String) {
        preferences.edit {
            putString(KEY_API, key)
        }
    }

    override fun getApiKey(): String? {
        return preferences.getString(KEY_API, "not_found")
    }

    override fun resetApiKey() {
        preferences.edit {
            remove(KEY_API)
        }
    }
}