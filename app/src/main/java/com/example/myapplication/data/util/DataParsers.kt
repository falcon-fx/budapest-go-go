package com.example.myapplication.data.util

import android.content.Context
import com.example.myapplication.data.db.RouteEntity
import com.example.myapplication.data.db.StopEntity
import com.example.myapplication.data.db.VehicleEntity
import com.google.transit.realtime.GtfsRealtime
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object DataParsers {
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

    fun extractAndParseZip(
        context: Context,
        zipResponseBody: ResponseBody,
        parserMap: Map<String, (List<String>) -> Unit>
    ) {
        val buffer = ByteArray(1024)
        ZipInputStream(zipResponseBody.byteStream()).use { zipInStream ->
            var entry: ZipEntry?
            while (zipInStream.nextEntry.also { entry = it } != null) {
                val fileName = entry!!.name
                val file = File(context.cacheDir, fileName)
                FileOutputStream(file).use { fileOutStream ->
                    var len: Int
                    while (zipInStream.read(buffer).also { len = it } > 0) {
                        fileOutStream.write(buffer, 0, len)
                    }
                }
                val lines = file.readLines()
                parserMap[fileName]?.invoke(lines)
            }
        }
    }

    //
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
                null
            }
        }
    }
}