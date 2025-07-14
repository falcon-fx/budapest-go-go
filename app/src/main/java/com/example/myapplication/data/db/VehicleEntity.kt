package com.example.myapplication.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.transit.realtime.GtfsRealtime

@Entity(
    tableName = "vehicle",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["curr_trip_id"],
            onDelete = ForeignKey.NO_ACTION // Preserve vehicle records even if trip is deleted
        ),
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["curr_route_id"],
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = StopEntity::class,
            parentColumns = ["id"],
            childColumns = ["curr_stop_id"],
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [Index("curr_trip_id"), Index("curr_route_id"), Index("curr_stop_id")]
)
data class VehicleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "curr_trip_id") val currTripId: String,
    @ColumnInfo(name = "curr_route_id") val currRouteId: String,
    @ColumnInfo(name = "curr_lat") val currLat: Float,
    @ColumnInfo(name = "curr_lng") val currLng: Float,
    @ColumnInfo(name = "curr_stop_seq") val currStopSeq: Int,
    @ColumnInfo(name = "curr_status") val currStatus: String,
    @ColumnInfo(name = "curr_stop_id") val currStopId: String,
    val timestamp: Long
)
