package com.example.myapplication.data.util

import android.content.Context
import android.util.Log
import com.example.myapplication.data.db.RouteEntity
import com.example.myapplication.data.db.RouteTypes
import com.example.myapplication.data.db.StopEntity
import com.example.myapplication.data.db.TimetableEntity
import com.example.myapplication.data.db.TripEntity
import com.example.myapplication.data.db.VehicleEntity
import com.google.transit.realtime.GtfsRealtime
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object DataParsers {
    private val logTag = "DATAPARSERS"
    fun parseVehiclesRealtimeFromProtobuf(input: InputStream): List<VehicleEntity> {
        val feed = GtfsRealtime.FeedMessage.parseFrom(input)
        return feed.entityList.mapNotNull { entity ->
            try {
                val vehicle = entity.vehicle
                val trip = vehicle.trip
                val position = vehicle.position
                VehicleEntity(
                    id = vehicle.vehicle.id,
                    currTripId = trip.tripId,
                    currRouteId = trip.routeId,
                    currLat = position.latitude,
                    currLng = position.longitude,
                    currStopSeq = vehicle.currentStopSequence,
                    currStatus = vehicle.currentStatus.name,
                    currStopId = vehicle.stopId,
                    timestamp = vehicle.timestamp
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun extractAndParseZip(
        cacheDir: File,
        zipResponseBody: ResponseBody,
        parserMap: Map<String, suspend (List<String>) -> Unit>
    ) {
        val buffer = ByteArray(1024)
        ZipInputStream(zipResponseBody.byteStream()).use { zipInStream ->
            var entry: ZipEntry?
            while (zipInStream.nextEntry.also { entry = it } != null) {
                val fileName = entry!!.name
                val file = File(cacheDir, fileName)
                FileOutputStream(file).use { fileOutStream ->
                    var len: Int
                    while (zipInStream.read(buffer).also { len = it } > 0) {
                        fileOutStream.write(buffer, 0, len)
                    }
                }
                val lines = file.readLines()
                Log.i(logTag, "Reading $fileName, $lines")
                parserMap[fileName]?.invoke(lines)
            }
        }
    }

    // Parser helpers
    fun parseStops(lines: List<String>): List<StopEntity> {
        if (lines.isEmpty()) return emptyList()

        val cols = lines[0].split(",")
        val colIndex = cols.withIndex().associate { it.value to it.index }
        return lines.drop(1).mapNotNull { line ->
            val tokens = line.split(",")
            try {
                StopEntity(
                    id = tokens[colIndex["stop_id"] ?: return@mapNotNull null],
                    name = tokens[colIndex["stop_name"] ?: return@mapNotNull null],
                    lat = tokens[colIndex["stop_lat"] ?: return@mapNotNull null].toDouble(),
                    lon = tokens[colIndex["stop_lon"] ?: return@mapNotNull null].toDouble()
                )
            } catch (e: Exception) {
                Log.i(logTag, "parseStops exception: $e")
                null
            }
        }
    }

    fun parseRoutes(lines: List<String>): List<RouteEntity> {
        if (lines.isEmpty()) return emptyList()

        val cols = lines[0].split(",")
        val colIndex = cols.withIndex().associate { it.value to it.index }
        return lines.drop(1).mapNotNull { line ->
            val tokens = line.split(",")
            try {
                RouteEntity(
                    id = tokens[colIndex["route_id"] ?: return@mapNotNull null],
                    shortName = tokens[colIndex["route_short_name"] ?: return@mapNotNull null],
                    desc = tokens[colIndex["route_desc"] ?: return@mapNotNull null],
                    type = RouteTypes.entries.first {
                        it.typeInt == tokens[colIndex["route_type"] ?: return@mapNotNull null].toInt()
                    },
                    color = tokens[colIndex["route_color"] ?: return@mapNotNull null],
                    textColor = tokens[colIndex["route_text_color"] ?: return@mapNotNull null],
                )
            } catch (e: Exception) {
                Log.i(logTag, "parseRoutes exception: $e")
                null
            }
        }
    }

    fun parseTrips(lines: List<String>): List<TripEntity> {
        if (lines.isEmpty()) return emptyList()

        val cols = lines[0].split(",")
        val colIndex = cols.withIndex().associate { it.value to it.index }
        return lines.drop(1).mapNotNull { line ->
            val tokens = line.split(",")
            try {
                TripEntity(
                    id = tokens[colIndex["trip_id"] ?: return@mapNotNull null],
                    routeId = tokens[colIndex["route_id"] ?: return@mapNotNull null],
                    headsign = tokens[colIndex["trip_headsign"] ?: return@mapNotNull null],
                    directionId = tokens[colIndex["direction_id"] ?: return@mapNotNull null].toInt() == 0
                )
            } catch (e: Exception) {
                Log.i(logTag, "parseTrips exception: $e")
                null
            }
        }
    }

    fun parseTimetable(lines: List<String>): List<TimetableEntity> {
        if (lines.isEmpty()) return emptyList()

        val cols = lines[0].split(",")
        val colIndex = cols.withIndex().associate { it.value to it.index }
        return lines.drop(1).mapNotNull { line ->
            val tokens = line.split(",")
            try {
                TimetableEntity(
                    tripId = tokens[colIndex["trip_id"] ?: return@mapNotNull null],
                    stopId = tokens[colIndex["stop_id"] ?: return@mapNotNull null],
                    arrTime = tokens[colIndex["arrival_time"] ?: return@mapNotNull null],
                    depTime = tokens[colIndex["departure_time"] ?: return@mapNotNull null],
                    stopSeq = tokens[colIndex["stop_sequence"] ?: return@mapNotNull null].toInt()
                )
            } catch (e: Exception) {
                Log.i(logTag, "parseTimetable exception: $e")
                null
            }
        }
    }
}