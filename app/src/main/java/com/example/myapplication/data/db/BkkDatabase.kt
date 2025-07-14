package com.example.myapplication.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.myapplication.data.db.dao.TimetableDao
import com.example.myapplication.data.db.dao.VehicleDao
import com.example.myapplication.data.util.BkkTypeConverters


@Database(
    entities = [
        RouteEntity::class,
        StopEntity::class,
        TimetableEntity::class,
        TripEntity::class,
        VehicleEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(
    value = [BkkTypeConverters::class]
)
abstract class BkkDatabase: RoomDatabase() {
    abstract val vehicleDao: VehicleDao
    abstract val timetableDao: TimetableDao
}