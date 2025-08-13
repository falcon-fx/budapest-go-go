package com.example.myapplication.data

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.room.Room
import com.example.myapplication.R
import com.example.myapplication.data.api.BkkApiService
import com.example.myapplication.data.db.BkkDatabase
import com.example.myapplication.data.db.dao.TimetableDao
import com.example.myapplication.data.db.repo.AuthRepo
import com.example.myapplication.data.db.repo.ProdAuthRepo
import com.example.myapplication.data.db.repo.ProdTimetableRepo
import com.example.myapplication.data.db.repo.ProdVehicleRepo
import com.example.myapplication.data.db.repo.TimetableRepo
import com.example.myapplication.data.db.repo.VehicleRepo
import com.example.myapplication.data.util.Tls12SocketFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.KeyStore
import javax.inject.Singleton
import javax.net.ssl.KeyStoreBuilderParameters
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

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
    @Provides
    @Singleton
    fun provideAuthRepo(@ApplicationContext context: Context): AuthRepo {
        return ProdAuthRepo(context)
    }
    // BKK Futár API
    @Provides
    @Singleton
    fun provideBaseUrl(@ApplicationContext context: Context): String {
        return context.getString(R.string.api_base_url)
    }
    @Provides
    @Singleton
    fun provideCompatibleOkHttpClient(): OkHttpClient {
        var builder = OkHttpClient.Builder()

        if(Build.VERSION.SDK_INT in 16..21) {
            val sslContext = SSLContext.getInstance("TLSv1.2")
            sslContext.init(null, null, null)

            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(null as KeyStore?)
            val trustManagers = trustManagerFactory.trustManagers

            val x509TrustManager = trustManagers.filterIsInstance<X509TrustManager>().first()

            val tlsSocketFactory = Tls12SocketFactory(sslContext.socketFactory)

            val tlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2)
                .build()
            return builder
                .sslSocketFactory(tlsSocketFactory, x509TrustManager)
                .connectionSpecs(listOf(tlsSpec, ConnectionSpec.CLEARTEXT))
                .build()
        } else {
            return builder.build()
        }
    }
    @Provides
    @Singleton
    fun provideRetrofit(
        baseUrl: String,
        okHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    @Provides
    @Singleton
    fun provideBkkApi(retrofit: Retrofit): BkkApiService {
        return retrofit.create(BkkApiService::class.java)
    }
}