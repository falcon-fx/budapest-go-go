package com.example.myapplication.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

interface BkkApiService {
    @GET("static/v1/public-gtfs/budapest_gtfs.zip")
    @Streaming
    suspend fun downloadTimetable(): Response<ResponseBody>

    @GET("query/v1/ws/gtfs-rt/full/VehiclePositions.pb")
    @Streaming
    suspend fun downloadVehiclePositions(
        @Query("key") apiKey: String
    ): Response<ResponseBody>
}
