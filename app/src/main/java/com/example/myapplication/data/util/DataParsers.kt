package com.example.myapplication.data.util

import android.util.Log
import com.example.myapplication.data.db.VehicleEntity
import com.google.transit.realtime.GtfsRealtime
import java.io.InputStream

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
}