package com.example.purrytify.data.api

import android.content.Context
import android.util.Log
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Arrays
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object RetrofitClient {
    private const val BASE_URL = "http://34.101.226.132:3000/"
    private const val TAG = "RetrofitClient"
    private var authInterceptor: AuthInterceptor? = null
    private var apiServiceInstance: ApiService? = null
    private var refreshClientInstance: ApiService? = null
    private var retrofitInstance: Retrofit? = null

    private val trustAllCerts = arrayOf<TrustManager>(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    )

    private val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, trustAllCerts, SecureRandom())
    }

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d(TAG, message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private fun createBaseHttpClient(): OkHttpClient.Builder {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
            .protocols(listOf(Protocol.HTTP_1_1))
    }

    fun initialize(context: Context) {
        if (authInterceptor == null) {
            authInterceptor = AuthInterceptor(context)
        }
    }
    
    fun getInstance(context: Context): Retrofit {
        if (retrofitInstance == null) {
            if (authInterceptor == null) {
                initialize(context)
            }
            
            val httpClient = createBaseHttpClient()
                .addInterceptor(authInterceptor!!)
                .build()

            retrofitInstance = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofitInstance!!
    }

    // For general API requests (with auth interceptor)
    val apiService: ApiService
        get() {
            if (apiServiceInstance == null) {
                if (authInterceptor == null) {
                    throw IllegalStateException("RetrofitClient not initialized. Call initialize() first.")
                }
                
                val httpClient = createBaseHttpClient()
                    .addInterceptor(authInterceptor!!)
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(httpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                apiServiceInstance = retrofit.create(ApiService::class.java)
            }
            return apiServiceInstance!!
        }

    // For token refresh only (without auth interceptor to avoid loops)
    fun getRefreshClient(): ApiService {
        if (refreshClientInstance == null) {
            val httpClient = createBaseHttpClient().build()
            
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            refreshClientInstance = retrofit.create(ApiService::class.java)
        }
        return refreshClientInstance!!
    }
}