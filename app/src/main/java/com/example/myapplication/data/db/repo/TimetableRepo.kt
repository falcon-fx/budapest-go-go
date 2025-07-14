package com.example.myapplication.data.db.repo

import com.example.myapplication.data.db.RouteEntity
import com.example.myapplication.data.db.RouteTypes
import com.example.myapplication.data.db.StopEntity
import com.example.myapplication.data.db.TimetableEntity
import com.example.myapplication.data.db.TripEntity

interface TimetableRepo {
    suspend fun fetchAndStoreTimetable(apiKey: String = "")
    suspend fun getStopsOfRoute(routeId: String, orderBySequence: Boolean, reverse: Boolean): List<StopEntity>
    suspend fun getCurrentStopOfVehicle(vehicleId: String): StopEntity
    suspend fun getRouteById(routeId: String): RouteEntity
    suspend fun getTripByRouteId(routeId: String): TripEntity
    suspend fun getTimesForRoute(routeId: String): List<TimetableEntity>
    suspend fun getTypeOfRoute(routeId: String): RouteTypes
}