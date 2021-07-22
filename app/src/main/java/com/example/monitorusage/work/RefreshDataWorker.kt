/*
 * Copyright (C) 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.monitorusage.work

import android.Manifest
import android.app.ActivityManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.icu.util.Calendar
import android.location.Location
import android.os.BatteryManager
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.monitorusage.MainActivity
import com.example.monitorusage.R
import com.example.monitorusage.model.TabletInfo
import com.example.monitorusage.network.RestService
import com.example.monitorusage.showSnackbar
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import retrofit2.HttpException
import timber.log.Timber
import java.text.DecimalFormat

class RefreshDataWorker(appContext: Context, params: WorkerParameters) :
        CoroutineWorker(appContext, params) {

    private lateinit var sharedPreferences: SharedPreferences
    companion object {
        const val WORK_NAME = "com.example.monitorusage.work.RefreshDataWorker"
    }
    override suspend fun doWork(): Result {
        //val credential = TabletCredential.getCredential()
        //val repository = VideosRepository(database)
        val installedApps = getInstalledApps()
        val batteryStatus = getBatteryStatus()
        val memoryUsage = getAvailableMemory(applicationContext)
        val freeSpace = getFreeSpace()
        val appUsage = getAppUsage()
        val imei = MainActivity.IMEI
        val serial = MainActivity.SERIAL
/*
        this.applicationContext.getSharedPreferences("imei", Context.MODE_PRIVATE)
            .also { sharedPreferences = it }

        Timber.d("Workmanager: serial shared on refresh: %s",R.string.preference_file_imei.toString())
        val imei =  sharedPreferences.getString(
            "imei","defaultImei"
        ).toString()

        this.applicationContext.getSharedPreferences("serial", Context.MODE_PRIVATE)
            .also { sharedPreferences = it }
        val serial =  sharedPreferences.getString(
            "serial","defaultSerial"
        ).toString()
*/

        //val location = getLatestLocation()
        // val serial = "T200021050642701"
        // val imei = "358782670000224"

        var fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)

        Timber.d("WorkManager: Work request for setup is run")
        try {
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) //{
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.

            //}
            {
            }
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    // Got last known location. In some rare situations this can be null.
                    Timber.d("WorkManager: Location -> %s", location?.latitude.toString())
                    Timber.d("WorkManager: Work request for sync is run")
                    Timber.d("WorkManager: Installed apps -> %s", installedApps.toString())
                    Timber.d("WorkManager: Battery status-> %s", batteryStatus)
                    Timber.d("WorkManager: Memory usage-> %s", memoryUsage)
                    Timber.d("WorkManager: Free space -> %s", freeSpace )
                    Timber.d("WorkManager: App usage -> %s", appUsage )
                    Timber.d("WorkManager: imei -> %s , serial -> %s", imei, serial )
                    // Send to server
                    try {
                        addTabletUsage(latitude = location?.latitude.toString(), longitude = location?.longitude.toString(),
                        batteryStatus = batteryStatus, freeSpace = freeSpace.toString(), imei = imei, installedApps = installedApps,
                        memoryUsage = memoryUsage, serial = serial, appUsage = appUsage)
                    }catch (e : Exception){
                        Timber.d("Workmanager: error addTabletUsage() -> %s", e.toString())
                    }


                }

            //Timber.d("WorkManager: location -> latitude %s", location.result.latitude)

        } catch (e: HttpException) {
            Timber.d("WorkManager: Work request for retry is run")
            return Result.retry()
        }

        return Result.success()
    }

    private fun getAppUsage(): String {
        // get Usage for installed apps

        val apps: MutableList<ApplicationInfo> = applicationContext.packageManager.getInstalledApplications(0)
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)


        val listOfInstalledNames = mutableListOf<String>()
        val listOfInstalledPackageNames = mutableListOf<String>()
        val listOfInstalledApps= mutableListOf<ApplicationInfo>()

        var isAppInSystemPartition:Boolean? = false
        var isSignedBySystem:Boolean?
        //installedApps.text = apps[0].packageName.toString()
        //val piSys: MutableList<ApplicationInfo> = packageManager.getInstalledApplications(GET_UNINSTALLED_PACKAGES )
        val piSys = applicationContext.packageManager.getPackageInfo("android", PackageManager.GET_SIGNING_CERTIFICATES)


        //Get list of installed apps not belonging to the system

        for (app in apps) {
            /*
            if (!sysApps.contains(app)){
                diff.add(app)
            }
            */
            //isAppInSystemPartition = false

            try {
                if (app.labelRes != 0
                    && !app.packageName.isNullOrBlank()
                    && !applicationContext.packageManager.getLaunchIntentForPackage(app.packageName).toString().isNullOrBlank()
                    && !applicationContext.packageManager.getApplicationLabel(app).toString().isNullOrEmpty()) {
                    isAppInSystemPartition = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) > 0
                    val appPackageInfo = applicationContext.packageManager.getPackageInfo(
                        app.packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES
                    )

                    val usageStats = UsageStats.CREATOR
                    isSignedBySystem = piSys.signingInfo.equals(appPackageInfo)
                    if (isAppInSystemPartition != null) {
                        if (isAppInSystemPartition == false && isSignedBySystem ==false){
                            listOfInstalledNames.add(applicationContext.packageManager.getApplicationLabel(app).toString())
                            listOfInstalledPackageNames.add(app.packageName)
                            listOfInstalledApps.add(app)
                        }

                    }
                }
            }catch ( e: Exception ){
                if(applicationContext != null){
                    Toast.makeText(applicationContext,e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }
        }


        val statsManager = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        var list: MutableList<UsageStats>
        val cal = Calendar.getInstance()
        cal.add(Calendar.YEAR, -1)

        list = statsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, cal.getTimeInMillis(), System.currentTimeMillis())
        val usageList = mutableListOf<String>()
        for (usage in list){
            if (usage.packageName in listOfInstalledPackageNames)
                for (usageApp in listOfInstalledApps){
                    if (usage.packageName == usageApp.packageName){
                        val df = DecimalFormat("#.##")

                        val minutes : Long = usage.totalTimeVisible
                        val hours = (minutes / 3600000).toFloat()

                        //val number2digits : Double = String.format("%.2f", hours).toDouble()

                        usageList.add("${applicationContext.packageManager.getApplicationLabel(usageApp).toString()}" +
                                ": " + "%.2f".format(hours))
                    }
                }
        }
        if (usageList.isNullOrEmpty()){

           return "Debe habilitar permiso de uso para funcionamiento central"
        }else{
            return usageList.distinct().toString()
        }
    }
    private fun getLatestLocation(): Task<Location> {
        var lastLatitude : String
        var lastLongitude : String
        var fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) //{
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.

        //}
        {
        }
        val addOnSuccessListener = fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                // Got last known location. In some rare situations this can be null.
                Timber.d("WorkManager: Location -> %s", location.toString())
                return@addOnSuccessListener
            }
        return addOnSuccessListener
    }

    private fun getInstalledApps(): String {
        val apps: MutableList<ApplicationInfo> = applicationContext.packageManager.getInstalledApplications(0)
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)


        val listOfInstalledNames = mutableListOf<String>()
        val listOfInstalledPackageNames = mutableListOf<String>()
        val listOfInstalledApps= mutableListOf<ApplicationInfo>()

        var isAppInSystemPartition:Boolean? = false
        var isSignedBySystem:Boolean?
        //installedApps.text = apps[0].packageName.toString()
        //val piSys: MutableList<ApplicationInfo> = packageManager.getInstalledApplications(GET_UNINSTALLED_PACKAGES )
        val piSys = applicationContext.packageManager.getPackageInfo("android", PackageManager.GET_SIGNING_CERTIFICATES)


        //Get list of installed apps not belonging to the system

        for (app in apps) {
            /*
            if (!sysApps.contains(app)){
                diff.add(app)
            }
            */
            //isAppInSystemPartition = false
            try {
                if (app.labelRes != 0
                    && !app.packageName.isNullOrBlank()
                    && !applicationContext.packageManager.getLaunchIntentForPackage(app.packageName).toString().isNullOrBlank()
                    && !applicationContext.packageManager.getApplicationLabel(app).toString().isNullOrEmpty()) {
                    isAppInSystemPartition = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) > 0
                    val appPackageInfo = applicationContext.packageManager.getPackageInfo(
                        app.packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES
                    )

                    val usageStats = UsageStats.CREATOR
                    isSignedBySystem = piSys.signingInfo.equals(appPackageInfo)
                    if (isAppInSystemPartition != null) {
                        if (isAppInSystemPartition == false && isSignedBySystem ==false){
                            listOfInstalledNames.add(applicationContext.packageManager.getApplicationLabel(app).toString())
                            listOfInstalledPackageNames.add(app.packageName)
                            listOfInstalledApps.add(app)
                        }

                    }
                }
            }catch ( e: Exception ){
                if(applicationContext != null){
                    Toast.makeText(applicationContext,e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }
        }

        return listOfInstalledNames.toString()
    }
    // get Battery Intent
    private fun getBatteryStatus(): String{
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            val context = applicationContext
            context.registerReceiver(null, ifilter)
        }

        //get Battery Percentage
        val batteryPct: Float? = batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toFloat()
        }

        return batteryPct.toString()
    }


    //get Memory Info RAM
    private fun getAvailableMemoryManager(appContext: Context): ActivityManager.MemoryInfo {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also { memoryInfo ->
            activityManager.getMemoryInfo(memoryInfo)
        }
    }

    private fun getAvailableMemory(appContext: Context): String{
        val availableMemory = getAvailableMemoryManager(appContext).availMem / 1024
        val totalMemory = getAvailableMemoryManager(appContext).totalMem / 1024
        val usedMemory = totalMemory - availableMemory

        val memoryUsage = "disponible: $availableMemory, total: $totalMemory, utilizado: $usedMemory"
        return memoryUsage
    }


    // free space
    private fun getFreeSpace(): Long {
        val freeInternalMemory = Environment.getDataDirectory()
        val freeExternalMemory = Environment.getDataDirectory().freeSpace
        val freeSpace = Environment.getRootDirectory().freeSpace
        //val totalSpace = Environment.getRootDirectory().totalSpace
        val totalFreeSpace = freeExternalMemory + freeSpace
        return totalFreeSpace
    }

    // send tablet usage
    fun addTabletUsage(imei: String, serial: String, latitude: String, longitude: String,
                       installedApps: String, batteryStatus: String, freeSpace: String,
                       memoryUsage: String, appUsage:String
    ) {
        val apiService = RestService()
        val tabletInfo = TabletInfo(
            imei = imei,
            serial = serial,
            latitude = latitude,
            longitude = longitude,
            installedApps = installedApps,
            batteryStatus = batteryStatus,
            freeSpace = freeSpace,
            memoryUsage = memoryUsage,
            appUsage = appUsage,
            result = null)

        apiService.addTabletUsage(tabletInfo) {
            Timber.d(it.toString())
            if (it?.result != null) {
                // it = newly added user parsed as response
                // it?.id = newly added user ID
                Timber.d("Workmanager: REST result = %s from %s", it?.result, it.imei)
                Toast.makeText(applicationContext,"Informacion correctamente enviada!", Toast.LENGTH_LONG).show()
            } else {
                Timber.d("Error registering new user")
                Toast.makeText(applicationContext,"Revise los datos, permisos y envie de nuevo", Toast.LENGTH_LONG).show()
            }
        }
    }




}

fun View.showSnackbar(
    view: View,
    msg: String,
    length: Int,
    actionMessage: CharSequence?,
    action: (View) -> Unit
) {
    val snackbar = Snackbar.make(view, msg, length)
    if (actionMessage != null) {
        snackbar.setAction(actionMessage) {
            action(this)
        }.show()
    } else {
        snackbar.show()
    }



}