package com.example.myapplication.ui.map

import android.app.ActivityManager
import android.content.Context.ACTIVITY_SERVICE
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.db.repo.AuthRepo
import com.example.myapplication.data.db.repo.TimetableRepo
import com.example.myapplication.data.db.repo.VehicleRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.math.log

@HiltViewModel
class MapViewModel @Inject constructor(
    private val timetable: TimetableRepo,
    private val auth: AuthRepo,
    private val vehicles: VehicleRepo
): ViewModel() {
    val logTag = "MAPSCREEN"
    private val batchSize = 5000
    enum class Screen { MAP, TIMETABLE }
    private val _currentScreen = MutableLiveData(Screen.MAP)
    val currentScreen: LiveData<Screen> = _currentScreen
    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    fun switchScreens(screen: Screen) { _currentScreen.value = screen }

    fun fetchTimetable(cacheDir: File) {
        viewModelScope.launch {
            _loading.value = true
            timetable.fetchAndStoreTimetable(cacheDir, batchSize)
            _loading.value = false
        }
    }

    fun loadRoutes() {
        viewModelScope.launch {
            Log.i(logTag, "loadRoutes called")
            val allRoutes = timetable.getAllRoutes()
            Log.i(logTag, "allRoutes: ${allRoutes}")
            for (route in allRoutes) {
                Log.i(logTag, route.desc)
            }
        }
    }
}