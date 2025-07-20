package com.example.myapplication.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.myapplication.data.db.RouteEntity
import com.example.myapplication.data.db.RouteTypes
import com.example.myapplication.data.db.StopEntity
import com.example.myapplication.data.db.TimetableEntity
import com.example.myapplication.data.db.TripEntity

@Dao
interface TimetableDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimetable(timetable: List<TimetableEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutes(routes: List<RouteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<StopEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrips(trips: List<TripEntity>)

    @Query("DELETE FROM timetable")
    suspend fun deleteTimetable()

    @Query("DELETE FROM routes")
    suspend fun deleteRoutes()

    @Query("DELETE FROM stops")
    suspend fun deleteStops()

    @Query("DELETE FROM trips")
    suspend fun deleteTrips()

    @Transaction
    suspend fun replaceTimetable(timetable: List<TimetableEntity>) {
        deleteTimetable()
        insertTimetable(timetable)
    }

    @Transaction
    suspend fun replaceRoutes(routes: List<RouteEntity>) {
        deleteRoutes()
        insertRoutes(routes)
    }

    @Transaction
    suspend fun replaceStops(stops: List<StopEntity>) {
        deleteStops()
        insertStops(stops)
    }

    @Transaction
    suspend fun replaceTrips(trips: List<TripEntity>) {
        deleteTrips()
        insertTrips(trips)
    }

    @Query("""
        SELECT stops.* FROM stops
        INNER JOIN timetable ON stops.id = timetable.stop_id
        INNER JOIN trips ON timetable.trip_id = trips.id
        WHERE trips.route_id = :routeId
        GROUP BY stops.id
        ORDER BY timetable.stop_seq ASC
    """)
    suspend fun getStopsOfRouteAsc(routeId: String): List<StopEntity>

    @Query("""
        SELECT stops.* FROM stops
        INNER JOIN timetable ON stops.id = timetable.stop_id
        INNER JOIN trips ON timetable.trip_id = trips.id
        WHERE trips.route_id = :routeId
        GROUP BY stops.id
        ORDER BY timetable.stop_seq DESC
    """)
    suspend fun getStopsOfRouteDesc(routeId: String): List<StopEntity>

    @Query("""
        SELECT * FROM routes ORDER BY type
    """)
    suspend fun getAllRoutes(): List<RouteEntity>

    @Query("""
        SELECT * FROM stops
        WHERE id = (
            SELECT curr_stop_id FROM vehicle
            WHERE vehicle.id = :vehicleId
            LIMIT 1
        )
    """)
    suspend fun getCurrentStopOfVehicle(vehicleId: String): StopEntity

    @Query("""
        SELECT * FROM stops
        WHERE id = :stopId
        LIMIT 1
    """)
    suspend fun getStopById(stopId: String): StopEntity

    @Query("""
        SELECT * FROM routes WHERE id = :routeId LIMIT 1
    """)
    suspend fun getRouteById(routeId: String): RouteEntity

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTripById(tripId: String): TripEntity

    @Query("SELECT * FROM trips WHERE route_id = :routeId")
    suspend fun getTripByRouteId(routeId: String): TripEntity

    @Query("""
        SELECT timetable.* FROM timetable
        INNER JOIN trips ON timetable.trip_id = trips.id
        WHERE trips.route_id = :routeId
        ORDER BY timetable.stop_seq ASC
    """)
    suspend fun getTimesForRouteAsc(routeId: String): List<TimetableEntity>

    @Query("""
        SELECT timetable.* FROM timetable
        INNER JOIN trips ON timetable.trip_id = trips.id
        WHERE trips.route_id = :routeId
        ORDER BY timetable.stop_seq DESC
    """)
    suspend fun getTimesForRouteDesc(routeId: String): List<TimetableEntity>

    @Query("SELECT type FROM routes WHERE id = :routeId LIMIT 1")
    suspend fun getTypeOfRoute(routeId: String): RouteTypes
}
