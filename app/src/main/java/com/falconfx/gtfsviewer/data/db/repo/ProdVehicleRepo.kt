package com.falconfx.gtfsviewer.data.db.repo

import com.falconfx.gtfsviewer.data.api.BkkApiService
import com.falconfx.gtfsviewer.data.db.VehicleEntity
import com.falconfx.gtfsviewer.data.db.dao.VehicleDao
import com.falconfx.gtfsviewer.data.util.DataParsers

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