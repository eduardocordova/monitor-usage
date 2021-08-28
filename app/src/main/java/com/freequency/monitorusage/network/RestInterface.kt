package com.freequency.monitorusage.network

import com.freequency.monitorusage.model.TabletInfo
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

// dev url
//private const val BASE_URL = "http://192.168.100.21:8080"


//private const val BASE_URL = "https://tablets.santaelena.gob.ec:8080"

//privte const val BASE_URL = "https://186.46.154.219:443"

//production url
private const val BASE_URL = "https://tablets.santaelena.gob.ec"
interface RestInterface {
        //@Headers("Content-Type: application/json")
        @POST("tablet/createTabletInfo")
        fun addTabletUsage(@Body tabletData: TabletInfo): Call<TabletInfo>
}

object ServiceBuilder {
        private val client = OkHttpClient.Builder().build()

        private val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL) // change this IP for testing by your actual machine IP
                .addConverterFactory(GsonConverterFactory.create())

                //.addConverterFactory(MoshiConverterFactory.create())
                //.client(client)

                .build()

        fun<T> buildService(service: Class<T>): T{
                return retrofit.create(service)
        }
}