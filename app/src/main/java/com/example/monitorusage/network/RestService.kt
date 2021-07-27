package com.example.monitorusage.network

import com.example.monitorusage.model.TabletInfo
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber

class RestService {

    fun addTabletUsage(tabletData: TabletInfo, onResult: (TabletInfo?) -> Unit){
        val retrofit = ServiceBuilder.buildService(RestInterface::class.java)
        //Timber.d("Workmanager REST Service retrofit %s", retrofit.toString())
        retrofit.addTabletUsage(tabletData).enqueue(
            object : Callback<TabletInfo> {
                override fun onFailure(call: Call<TabletInfo>, t: Throwable) {

                    Timber.d("Workmanager REST Service %s", t.localizedMessage)
                    onResult(null)
                }
                override fun onResponse( call: Call<TabletInfo>, response: Response<TabletInfo>) {
                    val addTabletUsage = response.body()
                    Timber.d("Workmanager REST Service sucess %s", response.message())
                    onResult(addTabletUsage)
                }
            }
        )
    }

}