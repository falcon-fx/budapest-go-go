package com.example.myapplication.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.example.myapplication.R
import com.example.myapplication.data.api.BkkApiService
import com.example.myapplication.data.db.BkkDatabase
import com.example.myapplication.data.db.dao.TimetableDao
import com.example.myapplication.data.db.repo.ProdTimetableRepo
import com.example.myapplication.data.db.repo.ProdVehicleRepo
import com.example.myapplication.data.db.repo.TimetableRepo
import com.example.myapplication.data.db.repo.VehicleRepo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // Bkk Database
    @Provides
    @Singleton
    fun provideBkkDb(app: Application): BkkDatabase {
        return Room.databaseBuilder(
            app,
            BkkDatabase::class.java,
            "bkk_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    // Repos
    @Provides
    @Singleton
    fun provideTimetableRepo(database: BkkDatabase, apiService: BkkApiService): TimetableRepo {
        return ProdTimetableRepo(database.timetableDao, apiService)
    }
    @Provides
    @Singleton
    fun provideVehicleRepo(database: BkkDatabase, apiService: BkkApiService): VehicleRepo {
        return ProdVehicleRepo(database.vehicleDao, apiService)
    }
    // BKK Futár API
    @Provides
    @Singleton
    fun provideBaseUrl(@ApplicationContext context: Context): String {
        return context.getString(R.string.api_base_url)
    }
    @Provides
    @Singleton
    fun provideRetrofit(
        baseUrl: String
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    @Provides
    @Singleton
    fun provideBkkApi(retrofit: Retrofit): BkkApiService {
        return retrofit.create(BkkApiService::class.java)
    }
}