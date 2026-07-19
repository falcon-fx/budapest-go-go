package com.example.myapplication.data.db

import android.util.Log
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myapplication.data.db.dao.TimetableDao
import com.example.myapplication.data.db.dao.VehicleDao
import com.example.myapplication.data.util.BkkTypeConverters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


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
    private val LOGTAG = "BkkDatabase"
    abstract val vehicleDao: VehicleDao
    abstract val timetableDao: TimetableDao

    suspend fun fastClearAll() {
        withContext(Dispatchers.IO) {
            val db = openHelper.writableDatabase

            // Disable FK temporarily (API 18+ compatible)
            db.execSQL("PRAGMA foreign_keys = OFF")

            // Delete all - order matters for FK safety if re-enabled
            db.execSQL("DELETE FROM timetable")
            Log.i(LOGTAG, "deleted timetable")

            db.execSQL("DELETE FROM trips")
            Log.i(LOGTAG, "deleted trips")

            db.execSQL("DELETE FROM routes")
            Log.i(LOGTAG, "deleted routes")

            db.execSQL("DELETE FROM stops")
            Log.i(LOGTAG, "deleted stops")

            // Single checkpoint at end
            db.query("PRAGMA journal_mode", emptyArray()).use { c ->
                if (c.moveToFirst() && c.getString(0).equals("wal", ignoreCase = true)) {
                    db.query("PRAGMA wal_checkpoint(TRUNCATE)", emptyArray()).close()
                }
            }

            // Re-enable FK
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    }
}