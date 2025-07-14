package com.example.myapplication.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(
            entity = RouteEntity::class,
            parentColumns = ["id"],
            childColumns = ["route_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("route_id")]
)
data class TripEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "route_id") val routeId: String,
    val headsign: String,
    @ColumnInfo(name = "direction_id") val directionId: Boolean
)
