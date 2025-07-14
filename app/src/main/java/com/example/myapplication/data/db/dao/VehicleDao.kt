package com.example.myapplication.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.myapplication.data.db.VehicleEntity

@Dao
interface VehicleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicles(vehicles: List<VehicleEntity>)

    @Query("DELETE FROM vehicle")
    suspend fun deleteVehicles()

    @Transaction
    suspend fun replaceVehicles(vehicles: List<VehicleEntity>) {
        deleteVehicles()
        insertVehicles(vehicles)
    }

    @Query("SELECT * FROM vehicle WHERE id = :vehicleId LIMIT 1")
    suspend fun getVehicleById(vehicleId: String): VehicleEntity

    @Query("SELECT * FROM vehicle")
    suspend fun getAllVehicles(): List<VehicleEntity>
}
