package com.example.myapplication.data.util

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
import kotlin.math.log

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



    // Parser helpers
    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        fun flushField() {
            result.add(current.toString())
            current.clear()
        }

        while (i < line.length) {

            when (val currChar = line[i]) {
                '"' -> {
                    if(inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' -> if (!inQuotes) {
                    flushField()
                }
                else -> current.append(currChar)
            }
            i++
        }
        if(inQuotes) {
            Log.w(logTag, "parseCsvLine: malformed CSV, unterminated quote in $line")
            return emptyList()
        }
        flushField()
        return result
    }

    fun parseStops(lines: Sequence<String>): List<StopEntity> {
        val iterator = lines.iterator()
        if (!iterator.hasNext()) return emptyList()

        val cols = parseCsvLine(iterator.next())
        val colIndex = cols.withIndex().associate { it.value to it.index }
        return iterator.asSequence().mapNotNull { line ->
            val tokens = parseCsvLine(line)
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
        }.toList()
    }

    fun parseRoutes(lines: Sequence<String>): List<RouteEntity> {
        val iterator = lines.iterator()
        if (!iterator.hasNext()) return emptyList()

        val cols = parseCsvLine(iterator.next())
        val colIndex = cols.withIndex().associate { it.value to it.index }
        return iterator.asSequence().mapNotNull { line ->
            val tokens = parseCsvLine(line)
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
        }.toList()
    }

    fun parseTrips(lines: Sequence<String>): List<TripEntity> {
        val iterator = lines.iterator()
        if (!iterator.hasNext()) return emptyList()

        val cols = parseCsvLine(iterator.next())
        val colIndex = cols.withIndex().associate { it.value to it.index }
        return iterator.asSequence().mapNotNull { line ->
            val tokens = parseCsvLine(line)
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
        }.toList()
    }

    fun parseTimetable(lines: Sequence<String>): List<TimetableEntity> {
        val iterator = lines.iterator()
        if (!iterator.hasNext()) return emptyList()

        val cols = parseCsvLine(iterator.next())
        val colIndex = cols.withIndex().associate { it.value to it.index }
        return iterator.asSequence().mapNotNull { line ->
            val tokens = parseCsvLine(line)
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
        }.toList()
    }
}