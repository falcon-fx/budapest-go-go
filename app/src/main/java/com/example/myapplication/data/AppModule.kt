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
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.KeyStoreBuilderParameters
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Helper
    private fun buildSslFactoryWithBundledCAs(context: Context, certResources: IntArray): Pair<SSLSocketFactory, X509TrustManager> {
        val certFactory = CertificateFactory.getInstance("X.509")
        val appKeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }

        certResources.forEachIndexed { idx, resourceId ->
            val inStream: InputStream = context.resources.openRawResource(resourceId)
            val cert = certFactory.generateCertificate(inStream) as X509Certificate
            inStream.close()
            appKeyStore.setCertificateEntry("app-ca-$idx", cert)
        }

        val tmfApp = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(appKeyStore) }

        val appTm = tmfApp.trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()

        val tmfSystem = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }

        val systemTm = tmfSystem.trustManagers
            .filterIsInstance<X509TrustManager>()
            .firstOrNull()
            ?: throw IllegalStateException("No system X509TrustManager available")

        val compositeTm = object : X509TrustManager {

            @Throws(CertificateException::class)
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                try {
                    systemTm.checkClientTrusted(chain, authType)
                } catch (ce: CertificateException) {
                    if (appTm != null) {
                        appTm.checkClientTrusted(chain, authType)
                    } else {
                        throw ce
                    }
                }
            }

            @Throws(CertificateException::class)
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                try {
                    systemTm.checkServerTrusted(chain, authType)
                } catch (se: CertificateException) {
                    if (appTm != null) {
                        appTm.checkServerTrusted(chain, authType) // may throw
                    } else {
                        throw se
                    }
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                val sys = systemTm.acceptedIssuers
                val app = appTm?.acceptedIssuers ?: emptyArray()
                return sys + app
            }

        }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(compositeTm), null)

        return Pair(sslContext.socketFactory, compositeTm)
    }

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
        return ProdTimetableRepo(apiService, database)
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
    fun provideCompatibleOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        var builder = OkHttpClient.Builder()

        val certResources = intArrayOf(R.raw.eszigno_root, R.raw.eszigno_intermediate, R.raw.go_bkk_hu)

        val (sslSocketFactory, trustManager) = buildSslFactoryWithBundledCAs(context, certResources)

        val finalSocketFactory: SSLSocketFactory = if (Build.VERSION.SDK_INT in 16..21) {
            Tls12SocketFactory(sslSocketFactory)
        } else {
            sslSocketFactory
        }

        builder.sslSocketFactory(finalSocketFactory, trustManager)

        val tlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2)
            .build()
        return builder
            .readTimeout(240, TimeUnit.SECONDS)
            .connectTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionSpecs(listOf(tlsSpec, ConnectionSpec.CLEARTEXT))
            .build()
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