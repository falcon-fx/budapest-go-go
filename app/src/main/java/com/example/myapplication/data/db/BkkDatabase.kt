package com.example.myapplication.data.db

import android.util.Log
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
            db.beginTransaction()
            Log.i(LOGTAG, "fastClearAll begun transaction")
            try {
                // Optional: turn off FK checks if you don’t need cascades here
                db.execSQL("PRAGMA foreign_keys=OFF")
                db.execSQL("PRAGMA journal_mode = DELETE")
                Log.i(LOGTAG, "fastClearAll foreign keys turned off")

                // Clear tables in dependency order (avoid FK violations)
                val tables = listOf("stops", "trips", "routes", "timetable")
                for (t in tables) {
                    try {
                        db.execSQL("DELETE FROM $t")
                        Log.i(LOGTAG, "fastClearAll deleted rows from $t")
                    } catch (e: Throwable) {
                        Log.e(LOGTAG, "fastClearAll failed deleting $t: ${e.message}", e)
                        throw e // abort transaction, will be handled in finally below
                    }
                }
                Log.i(LOGTAG, "fastClearAll deleted rows from all tables")

                db.setTransactionSuccessful()
                Log.i(LOGTAG, "fastClearAll transaction successful")
            } finally {
                if (db.inTransaction()) {
                    db.endTransaction()
                }
                db.execSQL("PRAGMA foreign_keys=ON")
                Log.i(LOGTAG, "fastClearAll foreign keys turned back on")
            }
        }
    }
}