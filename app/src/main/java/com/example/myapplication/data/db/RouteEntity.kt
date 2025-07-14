package com.example.myapplication.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "short_name") val shortName: String,
    val desc: String,
    val type: RouteTypes,
    val color: String,
    @ColumnInfo(name = "text_color") val textColor: String
)
