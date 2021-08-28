package com.freequency.monitorusage.model

import com.google.gson.annotations.SerializedName

data class TabletInfo (
    @SerializedName("imei") val imei: String?,
    @SerializedName("serial") val serial: String?,
    @SerializedName("latitude") val latitude: String?,
    @SerializedName("longitude") val longitude: String?,
    @SerializedName("installedApps") val installedApps: String?,
    @SerializedName("batteryStatus") val batteryStatus: String?,
    @SerializedName("freeSpace") val freeSpace: String?,
    @SerializedName("memoryUsage") val memoryUsage: String?,
    @SerializedName("appUsage") val appUsage: String?,
    @SerializedName("result") val result: String?,
    )



