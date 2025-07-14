package com.example.myapplication.data.db.repo

import com.example.myapplication.data.api.BkkApiService
import com.example.myapplication.data.db.RouteEntity
import com.example.myapplication.data.db.RouteTypes
import com.example.myapplication.data.db.StopEntity
import com.example.myapplication.data.db.TimetableEntity
import com.example.myapplication.data.db.TripEntity
import com.example.myapplication.data.db.dao.TimetableDao

class ProdTimetableRepo(
    private val timetableDao: TimetableDao,
    private val apiService: BkkApiService
): TimetableRepo {
    override suspend fun fetchAndStoreTimetable(apiKey: String) {
        TODO("Not yet implemented")
    }

    override suspend fun getStopsOfRoute(
        routeId: String,
        orderBySequence: Boolean,
        reverse: Boolean
    ): List<StopEntity> {
        TODO("Not yet implemented")
    }

    override suspend fun getCurrentStopOfVehicle(vehicleId: String): StopEntity {
        TODO("Not yet implemented")
    }

    override suspend fun getRouteById(routeId: String): RouteEntity {
        TODO("Not yet implemented")
    }

    override suspend fun getTripByRouteId(routeId: String): TripEntity {
        TODO("Not yet implemented")
    }

    override suspend fun getTimesForRoute(routeId: String): List<TimetableEntity> {
        TODO("Not yet implemented")
    }

    override suspend fun getTypeOfRoute(routeId: String): RouteTypes {
        TODO("Not yet implemented")
    }

}