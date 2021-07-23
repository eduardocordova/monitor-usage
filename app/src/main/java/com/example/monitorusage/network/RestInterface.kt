package com.example.monitorusage.network

import com.example.monitorusage.model.TabletInfo
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

// TODO set production url
private const val BASE_URL = "http://192.168.100.21:8080"


interface RestInterface {
        @Headers("Content-Type: application/json")
        @POST("createTabletInfo")
        fun addTabletUsage(@Body tabletData: TabletInfo): Call<TabletInfo>
}

object ServiceBuilder {
        private val client = OkHttpClient.Builder().build()

        private val retrofit = Retrofit.Builder()
                .baseUrl("http://192.168.100.21:8080/tablet/") // change this IP for testing by your actual machine IP
                .addConverterFactory(GsonConverterFactory.create())
                //.addConverterFactory(MoshiConverterFactory.create())
                .client(client)
                .build()

        fun<T> buildService(service: Class<T>): T{
                return retrofit.create(service)
        }
}