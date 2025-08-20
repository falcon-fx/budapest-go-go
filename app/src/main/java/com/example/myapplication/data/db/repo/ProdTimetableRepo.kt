package com.example.myapplication.data.db.repo

import android.util.Log
import androidx.room.withTransaction
import com.example.myapplication.data.api.BkkApiService
import com.example.myapplication.data.db.BkkDatabase
import com.example.myapplication.data.db.RouteEntity
import com.example.myapplication.data.db.RouteTypes
import com.example.myapplication.data.db.StopEntity
import com.example.myapplication.data.db.TimetableEntity
import com.example.myapplication.data.db.TripEntity
import com.example.myapplication.data.db.dao.TimetableDao
import com.example.myapplication.data.util.DataParsers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class ProdTimetableRepo(
    private val apiService: BkkApiService,
    private val bkkDatabase: BkkDatabase
): TimetableRepo {
    private val LOGTAG = "ProdTimetableRepo"
    private val dbWriteMutex = Mutex()
    
    private suspend fun replaceStopsInBatches(lines: Sequence<String>, batchSize: Int) {
        withContext(Dispatchers.IO) {
            val iterator = lines.iterator()
            if (!iterator.hasNext()) return@withContext
            val header = DataParsers.parseCsvLine(iterator.next())
            val columnIndex = header.withIndex().associate { it.value to it.index }
            var numOfBatches = 0
            val batch = mutableListOf<StopEntity>()
            
            for (line in iterator.asSequence()) {
                val tokens = DataParsers.parseCsvLine(line)
                if (tokens.isEmpty()) continue
                
                try {
                    batch += StopEntity(
                        id = tokens[columnIndex["stop_id"] ?: continue],
                        name = tokens[columnIndex["stop_name"] ?: continue],
                        lat = tokens[columnIndex["stop_lat"] ?: continue].toDouble(),
                        lon = tokens[columnIndex["stop_lon"] ?: continue].toDouble()
                    )
                } catch (e: Exception) {
                    Log.w(LOGTAG, "Stops: Skip malformed line: $e")
                }
                
                if (batch.size >= batchSize) {
                    bkkDatabase.timetableDao.insertStops(batch)
                    ++numOfBatches
                    Log.i(LOGTAG, "Inserted lines into Stops, $numOfBatches")
                    batch.clear()
                }
            }
            
            if (batch.isNotEmpty()) {
                bkkDatabase.timetableDao.insertStops(batch)
                ++numOfBatches
                Log.i(LOGTAG, "Inserted final lines into Stops, $numOfBatches")
                batch.clear()
            }
        }
    }

    private suspend fun replaceRoutesInBatches(lines: Sequence<String>, batchSize: Int) {
        withContext(Dispatchers.IO) {
            val iterator = lines.iterator()
            if (!iterator.hasNext()) return@withContext
            val header = DataParsers.parseCsvLine(iterator.next())
            val columnIndex = header.withIndex().associate { it.value to it.index }
            var numOfBatches = 0
            val batch = mutableListOf<RouteEntity>()

            for (line in iterator.asSequence()) {
                val tokens = DataParsers.parseCsvLine(line)
                if (tokens.isEmpty()) continue

                try {
                    batch += RouteEntity(
                        id = tokens[columnIndex["route_id"] ?: continue],
                        shortName = tokens[columnIndex["route_short_name"] ?: continue],
                        desc = tokens[columnIndex["route_desc"] ?: continue],
                        type = RouteTypes.entries.first { it.typeInt == tokens[columnIndex["route_type"] ?: 999].toInt() },
                        color = tokens[columnIndex["route_color"] ?: continue],
                        textColor = tokens[columnIndex["route_text_color"] ?: continue],
                    )
                } catch (e: Exception) {
                    Log.w(LOGTAG, "Routes: Skip malformed line: $e")
                }

                if (batch.size >= batchSize) {

                    bkkDatabase.timetableDao.insertRoutes(batch)
                    ++numOfBatches
                    Log.i(LOGTAG, "Inserted lines into Routes, $numOfBatches")
                    batch.clear()
                }
            }

            if (batch.isNotEmpty()) {
                bkkDatabase.timetableDao.insertRoutes(batch)
                ++numOfBatches
                Log.i(LOGTAG, "Inserted final lines into Routes, $numOfBatches")
                batch.clear()
            }
        }
    }

    private suspend fun replaceTripsInBatches(lines: Sequence<String>, batchSize: Int) {
        withContext(Dispatchers.IO) {
            val iterator = lines.iterator()
            if (!iterator.hasNext()) return@withContext
            val header = DataParsers.parseCsvLine(iterator.next())
            val columnIndex = header.withIndex().associate { it.value to it.index }
            var numOfBatches = 0
            val batch = mutableListOf<TripEntity>()

            for (line in iterator.asSequence()) {
                val tokens = DataParsers.parseCsvLine(line)
                if (tokens.isEmpty()) continue

                try {
                    batch += TripEntity(
                        id = tokens[columnIndex["trip_id"] ?: continue],
                        routeId = tokens[columnIndex["route_id"] ?: continue],
                        headsign = tokens[columnIndex["trip_headsign"] ?: continue],
                        directionId = tokens[columnIndex["direction_id"] ?: continue].toInt() == 0
                    )
                } catch (e: Exception) {
                    Log.w(LOGTAG, "Trips: Skip malformed line: $e")
                }

                if (batch.size >= batchSize) {

                    bkkDatabase.timetableDao.insertTrips(batch)
                    ++numOfBatches
                    Log.i(LOGTAG, "Inserted lines into Trips, $numOfBatches")
                    batch.clear()
                }
            }

            if (batch.isNotEmpty()) {
                bkkDatabase.timetableDao.insertTrips(batch)
                ++numOfBatches
                Log.i(LOGTAG, "Inserted final lines into Trips, $numOfBatches")
                batch.clear()
            }
        }
    }

    private suspend fun replaceTimetableInBatches(lines: Sequence<String>, batchSize: Int) {
        withContext(Dispatchers.IO) {
            val iterator = lines.iterator()
            var numOfBatches = 0
            if (!iterator.hasNext()) return@withContext
            val header = DataParsers.parseCsvLine(iterator.next())
            val columnIndex = header.withIndex().associate { it.value to it.index }

            val batch = mutableListOf<TimetableEntity>()

            for (line in iterator.asSequence()) {
                val tokens = DataParsers.parseCsvLine(line)
                if (tokens.isEmpty()) continue

                try {
                    batch += TimetableEntity(
                        tripId = tokens[columnIndex["trip_id"] ?: continue],
                        stopId = tokens[columnIndex["stop_id"] ?: continue],
                        arrTime = tokens[columnIndex["arrival_time"] ?: continue],
                        depTime = tokens[columnIndex["departure_time"] ?: continue],
                        stopSeq = tokens[columnIndex["stop_sequence"] ?: continue].toInt()
                    )
                } catch (e: Exception) {
                    Log.w(LOGTAG, "Skip malformed line: $e")
                }

                if (batch.size >= batchSize) {
                    bkkDatabase.timetableDao.insertTimetable(batch)
                    ++numOfBatches
                    Log.i(LOGTAG, "Inserted lines into Timetable, $numOfBatches")
                    batch.clear()
                }
            }

            if (batch.isNotEmpty()) {
                bkkDatabase.timetableDao.insertTimetable(batch)
                ++numOfBatches
                Log.i(LOGTAG, "Inserted final lines into Timetable, $numOfBatches")
                batch.clear()
            }
        }
    }

    private suspend fun saveResponseBodyToFile(responseBody: ResponseBody, dest: File) {
        withContext(Dispatchers.IO) {
            dest.parentFile?.mkdirs()

            val tmp = File(dest.parentFile, dest.name + ".tmp")
            if (tmp.exists()) tmp.delete()

            responseBody.byteStream().use { inputStream ->
                BufferedInputStream(inputStream).use { bufferedInStream ->
                    FileOutputStream(tmp).use { fileOutStream ->
                        val buffer = ByteArray(8 * 1024)
                        var read: Int
                        while (bufferedInStream.read(buffer).also { read = it } != -1) {
                            fileOutStream.write(buffer, 0, read)
                        }
                        try {
                            fileOutStream.fd.sync()
                        } catch (_: Throwable) {  }
                    }
                }
            }

            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
                if (!dest.exists()) throw java.io.IOException("Failed to move temp file ${tmp.path} -> ${dest.path}")
            }
        }
    }


    private suspend fun extractAndParseZip(
        cacheDir: File,
        zipResponseBody: ResponseBody,
        batchSize: Int
    ) {

        val zipFile = File(cacheDir, "timetable.zip").apply { parentFile?.mkdirs() }


        try {
            Log.i(LOGTAG, "Saving zip to ${zipFile.path}")
            saveResponseBodyToFile(zipResponseBody, zipFile)

            Log.i(LOGTAG, "Saved zip to ${zipFile.path}, size=${zipFile.length()}")
            if (!zipFile.exists() || zipFile.length() == 0L) {
                throw java.io.IOException("Saved zip file is empty or missing")
            }

            ZipInputStream(FileInputStream(zipFile)).use { zipInStream ->
                var entry: ZipEntry?
                while (zipInStream.nextEntry.also { entry = it } != null) {
                    val fileName = entry!!.name
                    Log.i(LOGTAG, "Extracting $fileName")
                    val file = File(cacheDir, fileName).apply { parentFile?.mkdirs() }
                    FileOutputStream(file).use { fileOutStream ->
                        val buf = ByteArray(8 * 1024)
                        var len: Int
                        while (zipInStream.read(buf).also { len = it } > 0) {
                            fileOutStream.write(buf, 0, len)
                        }
                        try {
                            fileOutStream.fd.sync()
                        } catch (_: Throwable) {
                        }
                    }
                    zipInStream.closeEntry()


                    file.bufferedReader().useLines { lines ->
                        Log.i(LOGTAG, "Reading $fileName")
                        when (fileName) {
                            "stops.txt" -> replaceStopsInBatches(lines, batchSize)
                            "routes.txt" -> replaceRoutesInBatches(lines, batchSize)
                            "trips.txt" -> replaceTripsInBatches(lines, batchSize)
                            "stop_times.txt" -> replaceTimetableInBatches(lines, batchSize)
                            else -> {
                                Log.i(LOGTAG, "Skipping $fileName")
                            }
                        }
                    }
                }
            }
        } catch(e: java.io.IOException) {
            Log.e(LOGTAG, "IO error while downloading/parsing: ${e.message}", e)
            try { zipFile.delete() } catch (_: Throwable) {}
            throw e
        } finally {
            try { zipResponseBody.close() } catch (_: Throwable) {}
        }

    }

    override suspend fun fetchAndStoreTimetable(cacheDir: File, batchSize: Int) {

        val response = apiService.downloadTimetable()
        if (response.isSuccessful && response.body() != null) {

            withContext(Dispatchers.IO) {
                dbWriteMutex.withLock {
                    Log.i(LOGTAG, "Starting DB wipe")
                    bkkDatabase.fastClearAll()
                    Log.i(LOGTAG, "Wiped DB")
                    extractAndParseZip(cacheDir, response.body()!!, batchSize)
                }


            }
        }
    }

    override suspend fun getStopsOfRoute(
        routeId: String,
        reverse: Boolean
    ): List<StopEntity> {
        return if (reverse) bkkDatabase.timetableDao.getStopsOfRouteDesc(routeId) else bkkDatabase.timetableDao.getStopsOfRouteAsc(routeId)
    }

    override suspend fun getAllRoutes(): List<RouteEntity> {
        return bkkDatabase.timetableDao.getAllRoutes()
    }

    override suspend fun getStopById(stopId: String): StopEntity {
        return bkkDatabase.timetableDao.getStopById(stopId)
    }

    override suspend fun getRouteById(routeId: String): RouteEntity {
        return bkkDatabase.timetableDao.getRouteById(routeId)
    }

    override suspend fun getTripByRouteId(routeId: String): TripEntity {
        return bkkDatabase.timetableDao.getTripByRouteId(routeId)
    }

    override suspend fun getTimesForRoute(routeId: String, reverse: Boolean): List<TimetableEntity> {
        return if (reverse) bkkDatabase.timetableDao.getTimesForRouteDesc(routeId) else bkkDatabase.timetableDao.getTimesForRouteAsc(routeId)
    }

    override suspend fun getTypeOfRoute(routeId: String): RouteTypes {
        return bkkDatabase.timetableDao.getTypeOfRoute(routeId)
    }
}