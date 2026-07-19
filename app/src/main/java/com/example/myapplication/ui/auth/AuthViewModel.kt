package com.example.myapplication.ui.auth

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.LiveData
import com.example.myapplication.data.db.repo.AuthRepo
import com.example.myapplication.data.db.repo.CertificateImportException
import com.example.myapplication.data.db.repo.CertificateRepo
import com.example.myapplication.ui.Event


@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepo,
    private val certRepo: CertificateRepo
): ViewModel() {
    val apiKey = MutableLiveData("")
    val logTag = "AUTH"

    private val _proceedToMap = MutableLiveData<Event<Unit>>()
    val proceedToMap: LiveData<Event<Unit>> = _proceedToMap

    private val _certImportError = MutableLiveData<Event<String>>()
    val certImportError: LiveData<Event<String>> = _certImportError

    private val _certImportSuccess = MutableLiveData<Event<Unit>>()
    val certImportSuccess: LiveData<Event<Unit>> = _certImportSuccess

    private val _requireRestart = MutableLiveData<Event<Unit>>()
    val requireRestart: LiveData<Event<Unit>> = _requireRestart

    fun hasCertificates(): Boolean = certRepo.hasCertificates()

    fun getCertificateCount(): Int = certRepo.getCertificateFiles().size

    fun importCertificates(fileBytes: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Repository handles all business logic: extraction, validation, storage
                certRepo.importCertificates(fileBytes)
                _certImportSuccess.postValue(Event(Unit))
                _requireRestart.postValue(Event(Unit))
            } catch (e: CertificateImportException) {
                _certImportError.postValue(Event(e.message ?: "Import failed"))
            } catch (e: Exception) {
                _certImportError.postValue(Event("Unexpected error: ${e.message}"))
            }
        }
    }

    fun saveApiKey() {
        apiKey.value?.let { authRepo.saveApiKey(it) }
        _proceedToMap.value = Event(Unit)

        Log.i(logTag, "Saved API key ${authRepo.getApiKey()}")
    }
}