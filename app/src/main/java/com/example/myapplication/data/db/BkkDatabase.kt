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

    private fun deleteTableInChunks(db: SupportSQLiteDatabase, table: String, chunkSize: Int = 10_000) {
        // Do not call this while you have an open transaction that spans multiple calls.
        while (true) {
            // Delete up to chunkSize rows using rowid selection
            val statement = db.compileStatement("DELETE FROM $table WHERE rowid IN (SELECT rowid FROM $table LIMIT ?)")
            statement.bindLong(1, chunkSize.toLong())
            val removed = statement.use {
                it.executeUpdateDelete()
            }
            if (removed == 0) break
            db.query("PRAGMA wal_checkpoint(TRUNCATE)", emptyArray()).close()
        }
    }

    suspend fun fastClearAll() {
        withContext(Dispatchers.IO) {
            val db = openHelper.writableDatabase
            deleteTableInChunks(db, "trips")
            Log.i(LOGTAG, "deleted trips")
            deleteTableInChunks(db, "routes")
            Log.i(LOGTAG, "deleted routes")
            deleteTableInChunks(db, "stops")
            Log.i(LOGTAG, "deleted stops")
            deleteTableInChunks(db, "timetable")
            Log.i(LOGTAG, "deleted timetable")
            db.query("PRAGMA journal_mode", emptyArray()).use { c ->
                if (c.moveToFirst() && c.getString(0).equals("wal", ignoreCase = true)) {
                    db.query("PRAGMA wal_checkpoint(TRUNCATE)", emptyArray()).close()
                }
            }
        }
    }
}