package com.example.myapplication.data.db.repo

import com.example.myapplication.data.api.BkkApiService
import com.example.myapplication.data.db.RouteEntity
import com.example.myapplication.data.db.RouteTypes
import com.example.myapplication.data.db.StopEntity
import com.example.myapplication.data.db.TimetableEntity
import com.example.myapplication.data.db.TripEntity
import com.example.myapplication.data.db.dao.TimetableDao
import com.example.myapplication.data.util.DataParsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ProdTimetableRepo(
    private val timetableDao: TimetableDao,
    private val apiService: BkkApiService
): TimetableRepo {
    override suspend fun fetchAndStoreTimetable(cacheDir: File) {
        val routesParser: suspend (List<String>) -> Unit = { lines ->
            timetableDao.replaceRoutes(DataParsers.parseRoutes(lines))
        }
        val stopsParser: suspend (List<String>) -> Unit = { lines ->
            timetableDao.replaceStops(DataParsers.parseStops(lines))
        }
        val tripsParser: suspend (List<String>) -> Unit = { lines ->
            timetableDao.replaceTrips(DataParsers.parseTrips(lines))
        }
        val timetableParser: suspend (List<String>) -> Unit = { lines ->
            timetableDao.replaceTimetable(DataParsers.parseTimetable(lines))
        }
        val parserMap: Map<String, suspend (List<String>) -> Unit> = mapOf(
            "routes.txt" to routesParser,
            "stops.txt" to stopsParser,
            "trips.txt" to tripsParser,
            "stop_times.txt" to timetableParser
        )
        val response = apiService.downloadTimetable()
        if (response.isSuccessful && response.body() != null) {
            withContext(Dispatchers.IO) {
                DataParsers.extractAndParseZip(cacheDir, response.body()!!, parserMap)
            }
        }
    }

    override suspend fun getStopsOfRoute(
        routeId: String,
        reverse: Boolean
    ): List<StopEntity> {
        return if (reverse) timetableDao.getStopsOfRouteDesc(routeId) else timetableDao.getStopsOfRouteAsc(routeId)
    }

    override suspend fun getAllRoutes(): List<RouteEntity> {
        return timetableDao.getAllRoutes()
    }

    override suspend fun getStopById(stopId: String): StopEntity {
        return timetableDao.getStopById(stopId)
    }

    override suspend fun getRouteById(routeId: String): RouteEntity {
        return timetableDao.getRouteById(routeId)
    }

    override suspend fun getTripByRouteId(routeId: String): TripEntity {
        return timetableDao.getTripByRouteId(routeId)
    }

    override suspend fun getTimesForRoute(routeId: String, reverse: Boolean): List<TimetableEntity> {
        return if (reverse) timetableDao.getTimesForRouteDesc(routeId) else timetableDao.getTimesForRouteAsc(routeId)
    }

    override suspend fun getTypeOfRoute(routeId: String): RouteTypes {
        return timetableDao.getTypeOfRoute(routeId)
    }
}