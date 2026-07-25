package com.falconfx.gtfsviewer.data.db.repo

import com.falconfx.gtfsviewer.data.db.RouteEntity
import com.falconfx.gtfsviewer.data.db.RouteTypes
import com.falconfx.gtfsviewer.data.db.StopEntity
import com.falconfx.gtfsviewer.data.db.TimetableEntity
import com.falconfx.gtfsviewer.data.db.TripEntity
import java.io.File

interface TimetableRepo {
    suspend fun fetchAndStoreTimetable(
        cacheDir: File,
        batchSize: Int,
        progressCallback: (suspend (LoadingProgress) -> Unit)? = null
    )
    suspend fun getStopsOfRoute(routeId: String, directionId: Boolean, reverse: Boolean): List<StopEntity>
    suspend fun getAllRoutes(): List<RouteEntity>
    suspend fun getStopById(stopId: String): StopEntity
    suspend fun getRouteById(routeId: String): RouteEntity
    suspend fun getTripByRouteId(routeId: String): TripEntity
    suspend fun getTimesForRoute(routeId: String, reverse: Boolean): List<TimetableEntity>
    suspend fun getTypeOfRoute(routeId: String): RouteTypes
    suspend fun getFinalStopNameOfRoute(routeId: String, directionId: Boolean): String?
    suspend fun searchRouteIdsByStopName(query: String): List<String>
}