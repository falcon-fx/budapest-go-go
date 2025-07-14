package com.example.myapplication.data.db.repo

import com.example.myapplication.data.api.BkkApiService
import com.example.myapplication.data.db.VehicleEntity
import com.example.myapplication.data.db.dao.VehicleDao
import com.example.myapplication.data.util.DataParsers
import com.google.transit.realtime.GtfsRealtime

class ProdVehicleRepo(
    private val vehicleDao: VehicleDao,
    private val apiService: BkkApiService
): VehicleRepo {
    override suspend fun fetchAndStoreRealtimeData(apiKey: String) {
        val response = apiService.downloadVehiclePositions(apiKey)
        if (response.isSuccessful) {
            response.body()?.byteStream()?.use { input ->
                vehicleDao.replaceVehicles(DataParsers.parseVehiclesRealtimeFromProtobuf(input))
            }
        }
    }

    override suspend fun getVehicleById(vehicleId: String): VehicleEntity {
        TODO("Not yet implemented")
    }

    override suspend fun getAllVehicles(): List<VehicleEntity> {
        TODO("Not yet implemented")
    }
}