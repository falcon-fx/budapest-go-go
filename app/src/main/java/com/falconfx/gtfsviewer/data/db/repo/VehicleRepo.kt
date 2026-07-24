package com.falconfx.gtfsviewer.data.db.repo

import com.falconfx.gtfsviewer.data.db.VehicleEntity

interface VehicleRepo {
    suspend fun fetchAndStoreRealtimeData(apiKey: String = "")
    suspend fun getVehicleById(vehicleId: String): VehicleEntity
    suspend fun getAllVehicles(): List<VehicleEntity>
}