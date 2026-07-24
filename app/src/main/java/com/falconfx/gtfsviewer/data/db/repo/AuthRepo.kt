package com.falconfx.gtfsviewer.data.db.repo

interface AuthRepo {
    fun saveApiKey(key: String)

    fun getApiKey(): String?

    fun resetApiKey()
}