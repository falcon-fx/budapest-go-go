package com.example.myapplication.data.db.repo

interface AuthRepo {
    fun saveApiKey(key: String)

    fun getApiKey(): String?

    fun resetApiKey()
}