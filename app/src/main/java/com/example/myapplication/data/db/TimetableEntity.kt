package com.example.myapplication.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "timetable",
    primaryKeys = ["trip_id", "stop_id"],
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE // Remove timetable entries when the trip is deleted
        ),
        ForeignKey(
            entity = StopEntity::class,
            parentColumns = ["id"],
            childColumns = ["stop_id"],
            onDelete = ForeignKey.CASCADE // Remove timetable entries when the stop is deleted
        )
    ],
    indices = [Index("trip_id"), Index("stop_id")]
)
data class TimetableEntity(
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "stop_id") val stopId: String,
    @ColumnInfo(name = "arr_time") val arrTime: String,
    @ColumnInfo(name = "dep_time") val depTime: String,
    @ColumnInfo(name = "stop_seq") val stopSeq: Int
)
