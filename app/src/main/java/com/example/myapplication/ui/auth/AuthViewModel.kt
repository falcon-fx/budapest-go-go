package com.example.myapplication.ui.auth

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.LiveData
import com.example.myapplication.data.db.repo.AuthRepo
import com.example.myapplication.ui.Event

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepo
): ViewModel() {
    val apiKey = MutableLiveData("")
    val logTag = "AUTH"

    private val _proceedToMap = MutableLiveData<Event<Unit>>()
    val proceedToMap: LiveData<Event<Unit>> = _proceedToMap

    fun saveApiKey() {
        apiKey.value?.let { authRepo.saveApiKey(it) }
        _proceedToMap.value = Event(Unit)

        Log.i(logTag, "Saved API key ${authRepo.getApiKey()}")
    }
}