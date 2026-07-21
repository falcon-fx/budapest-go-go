package com.example.myapplication.ui.map

import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.db.RouteEntity
import com.example.myapplication.data.db.StopEntity
import com.example.myapplication.data.db.repo.AuthRepo
import com.example.myapplication.data.db.repo.CertificateRepo
import com.example.myapplication.data.db.repo.TimetableRepo
import com.example.myapplication.data.db.repo.VehicleRepo
import com.example.myapplication.ui.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.math.log

@HiltViewModel
class MapViewModel @Inject constructor(
    private val timetable: TimetableRepo,
    private val auth: AuthRepo,
    private val vehicles: VehicleRepo,
    private val certRepo: CertificateRepo
): ViewModel() {
    val logTag = "MAPSCREEN"
    private val batchSize = 50000
    enum class Screen { MAP, TIMETABLE }
    private val _currentScreen = MutableLiveData(Screen.MAP)
    val currentScreen: LiveData<Screen> = _currentScreen
    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _routes = MutableLiveData<List<RouteEntity>>(emptyList())
    val routes : LiveData<List<RouteEntity>> = _routes

    private val _certError = MutableLiveData<Event<String>>()
    val certError: LiveData<Event<String>> = _certError

    private val _loadingProgress = MutableLiveData<com.example.myapplication.data.db.repo.LoadingProgress>()
    val loadingProgress: LiveData<com.example.myapplication.data.db.repo.LoadingProgress> = _loadingProgress

    fun switchScreens(screen: Screen) { _currentScreen.value = screen }

    private fun requireCertsOrError(): Boolean {
        if (!certRepo.hasCertificates()) {
            _certError.value = Event("Certificates not found. Please import certificates from the setup screen.")
            return false
        }
        return true
    }

    fun fetchTimetable(cacheDir: File) {
        if (!requireCertsOrError()) return
        viewModelScope.launch {
            _loading.value = true

            timetable.fetchAndStoreTimetable(cacheDir, batchSize) { progress ->
                _loadingProgress.postValue(progress)
            }

            _loading.value = false
            loadRoutes()
        }
    }

    fun loadRoutes() {
        viewModelScope.launch {
            Log.i(logTag, "loadRoutes called")
            val allRoutes = timetable.getAllRoutes()
            Log.i(logTag, "allRoutes: ${allRoutes.size} routes, ${allRoutes}")
            _routes.postValue(allRoutes)
        }
    }

    suspend fun getStopsOfRoute(routeId: String, directionId: Boolean, reverse: Boolean): List<StopEntity> {
        return timetable.getStopsOfRoute(routeId, directionId, reverse)
    }

    suspend fun getFinalStopNameOfRoute(routeId: String, directionId: Boolean): String? {
        return timetable.getFinalStopNameOfRoute(routeId, directionId)
    }
}