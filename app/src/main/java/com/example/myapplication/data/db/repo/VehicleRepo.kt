package com.example.myapplication.data.db.repo

import com.example.myapplication.data.db.VehicleEntity

interface VehicleRepo {
    suspend fun fetchAndStoreRealtimeData(apiKey: String = "")
    suspend fun getVehicleById(vehicleId: String): VehicleEntity
    suspend fun getAllVehicles(): List<VehicleEntity>
}