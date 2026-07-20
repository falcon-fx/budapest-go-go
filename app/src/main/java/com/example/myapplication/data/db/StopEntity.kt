package com.example.myapplication.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stops",
    indices = [Index(value = ["name", "lat", "lon"], unique = true)]
)
data class StopEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lat: Double,
    val lon: Double
)
