package com.falconfx.gtfsviewer.ui.map

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falconfx.gtfsviewer.data.db.RouteEntity
import com.falconfx.gtfsviewer.data.db.RouteTypes
import com.falconfx.gtfsviewer.data.db.StopEntity
import com.falconfx.gtfsviewer.data.db.repo.AuthRepo
import com.falconfx.gtfsviewer.data.db.repo.CertificateRepo
import com.falconfx.gtfsviewer.data.db.repo.TimetableRepo
import com.falconfx.gtfsviewer.data.db.repo.VehicleRepo
import com.falconfx.gtfsviewer.ui.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SearchResult(
    val routes: List<RouteEntity>,
    val preExpandRouteId: String?
)

@HiltViewModel
class TransportLinesViewModel @Inject constructor(
    private val timetable: TimetableRepo,
    private val auth: AuthRepo,
    private val vehicles: VehicleRepo,
    private val certRepo: CertificateRepo
) : ViewModel() {
    val logTag = "TRANSPORT_LINES"
    private val batchSize = 50000

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _routes = MutableLiveData<List<RouteEntity>>(emptyList())
    val routes: LiveData<List<RouteEntity>> = _routes

    private val _certError = MutableLiveData<Event<String>>()
    val certError: LiveData<Event<String>> = _certError

    private val _loadingProgress = MutableLiveData<com.falconfx.gtfsviewer.data.db.repo.LoadingProgress>()
    val loadingProgress: LiveData<com.falconfx.gtfsviewer.data.db.repo.LoadingProgress> = _loadingProgress

    private val _searchResult = MutableLiveData<SearchResult>()
    val searchResult: LiveData<SearchResult> = _searchResult

    private val _searching = MutableLiveData(false)
    val searching: LiveData<Boolean> = _searching

    private val _typeColors = MutableLiveData<Map<RouteTypes, Pair<String, String>>>()
    val typeColors: LiveData<Map<RouteTypes, Pair<String, String>>> = _typeColors

    private var allRoutesCache: List<RouteEntity> = emptyList()
    private var searchQuery = ""
    private var selectedTypes = emptySet<RouteTypes>()

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
            allRoutesCache = timetable.getAllRoutes()
            Log.i(logTag, "allRoutes: ${allRoutesCache.size} routes")
            _routes.postValue(allRoutesCache)
            computeTypeColors()
            applyFilters()
        }
    }

    private fun computeTypeColors() {
        val colors = allRoutesCache
            .groupBy { it.type }
            .mapValues { (_, routes) ->
                val first = routes.first()
                first.color to first.textColor
            }
        _typeColors.postValue(colors)
    }

    fun search(query: String) {
        searchQuery = query.trim()
        applyFilters()
    }

    fun toggleType(type: RouteTypes) {
        selectedTypes = if (type in selectedTypes) {
            selectedTypes - type
        } else {
            selectedTypes + type
        }
        applyFilters()
    }

    private fun applyFilters() {
        val query = searchQuery
        val types = selectedTypes

        _searching.postValue(true)
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            var result = allRoutesCache
            var preExpandRouteId: String? = null

            if (types.isNotEmpty()) {
                result = result.filter { it.type in types }
            }

            if (query.isNotEmpty()) {
                val typeFilteredIds = result.map { it.id }.toSet()

                val shortNameStart = System.currentTimeMillis()
                val shortNameRouteIds = allRoutesCache
                    .filter { isRouteNameMatch(it.shortName, query) }
                    .map { it.id }
                    .toSet()
                    .intersect(typeFilteredIds)
                val shortNameTime = System.currentTimeMillis() - shortNameStart
                Log.d(logTag, "search: shortName match took ${shortNameTime}ms, matched ${shortNameRouteIds.size} routes")

                val stopNameStart = System.currentTimeMillis()
                val stopNameRouteIds = timetable.searchRouteIdsByStopName(query)
                    .toSet()
                    .intersect(typeFilteredIds)
                val stopNameTime = System.currentTimeMillis() - stopNameStart
                Log.d(logTag, "search: stopName query took ${stopNameTime}ms, matched ${stopNameRouteIds.size} routes")

                val matchedIds = shortNameRouteIds + stopNameRouteIds
                result = result.filter { it.id in matchedIds }

                if (stopNameRouteIds.size == 1) {
                    preExpandRouteId = stopNameRouteIds.first()
                }
            }

            val totalTime = System.currentTimeMillis() - startTime
            Log.d(logTag, "search: total took ${totalTime}ms, result=${result.size} routes")

            _searchResult.postValue(SearchResult(result, preExpandRouteId))
            _searching.postValue(false)
        }
    }

    private fun isRouteNameMatch(shortName: String, query: String): Boolean {
        if (shortName.startsWith(query, ignoreCase = true)) return true
        val digits = shortName.dropWhile { !it.isDigit() }
        return digits.isNotEmpty() && digits.startsWith(query)
    }

    suspend fun getStopsOfRoute(routeId: String, directionId: Boolean, reverse: Boolean): List<StopEntity> {
        return timetable.getStopsOfRoute(routeId, directionId, reverse)
    }

    suspend fun getFinalStopNameOfRoute(routeId: String, directionId: Boolean): String? {
        return timetable.getFinalStopNameOfRoute(routeId, directionId)
    }
}
