package com.example.monitorusage

import android.Manifest
import android.app.ActivityManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.*
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.icu.util.Calendar
import android.location.Location
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.monitorusage.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.math.round

private const val TAG = "MainActivity"
private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var layout: View
    private lateinit var binding: ActivityMainBinding

    private var foregroundOnlyLocationServiceBound = false

    // Provides location updates for while-in-use feature.
    private var foregroundOnlyLocationService: ForegroundOnlyLocationService? = null

    // Listens for location broadcasts from ForegroundOnlyLocationService.
    private lateinit var foregroundOnlyBroadcastReceiver: ForegroundOnlyBroadcastReceiver

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var foregroundOnlyLocationButton: Button
    private lateinit var foregroundOnlyUsageStatsButton: Button

    private lateinit var outputTextView: TextView

    private lateinit var foreground_only_usage_stats_button : Button

    // Monitors connection to the while-in-use service.
    private val foregroundOnlyServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as ForegroundOnlyLocationService.LocalBinder
            foregroundOnlyLocationService = binder.service
            foregroundOnlyLocationServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            foregroundOnlyLocationService = null
            foregroundOnlyLocationServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        layout = binding.mainLayout
        setContentView(view)

        foregroundOnlyBroadcastReceiver = ForegroundOnlyBroadcastReceiver()

        sharedPreferences =
            getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)

        foregroundOnlyLocationButton = binding.foregroundOnlyLocationButton
        outputTextView = binding.outputTextView

        foregroundOnlyUsageStatsButton = binding.foregroundOnlyUsageStatsButton

        foregroundOnlyLocationButton.setOnClickListener {
            val enabled = sharedPreferences.getBoolean(
                SharedPreferenceUtil.KEY_FOREGROUND_ENABLED, false)

            if (enabled) {
                foregroundOnlyLocationService?.unsubscribeToLocationUpdates()
            } else {

                // Step 1.0, Review Permissions: Checks and requests if needed.
                if (foregroundPermissionApproved()) {
                    foregroundOnlyLocationService?.subscribeToLocationUpdates()
                        ?: Log.d(TAG, "Service Not Bound")
                } else {
                    requestForegroundPermissions()
                }
            }
        }


        //get installed apps
        val apps: MutableList<ApplicationInfo> = packageManager.getInstalledApplications(0)
        val mainIntent = Intent(Intent.ACTION_MAIN, null)
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)


        val listOfInstalledNames = mutableListOf<String>()
        val listOfInstalledPackageNames = mutableListOf<String>()
        val listOfInstalledApps= mutableListOf<ApplicationInfo>()

        var isAppInSystemPartition:Boolean? = false
        var isSignedBySystem:Boolean?
        //installedApps.text = apps[0].packageName.toString()
        //val piSys: MutableList<ApplicationInfo> = packageManager.getInstalledApplications(GET_UNINSTALLED_PACKAGES )
        val piSys = packageManager.getPackageInfo("android", PackageManager.GET_SIGNING_CERTIFICATES)


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
                    && !packageManager.getLaunchIntentForPackage(app.packageName).toString().isNullOrBlank()
                    && !packageManager.getApplicationLabel(app).toString().isNullOrEmpty()) {
                    isAppInSystemPartition = app.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) > 0
                    val appPackageInfo = packageManager.getPackageInfo(
                        app.packageName,
                        PackageManager.GET_SIGNING_CERTIFICATES
                    )

                    val usageStats = UsageStats.CREATOR
                    isSignedBySystem = piSys.signingInfo.equals(appPackageInfo)
                    if (isAppInSystemPartition != null) {
                        if (isAppInSystemPartition == false && isSignedBySystem ==false){
                            listOfInstalledNames.add(packageManager.getApplicationLabel(app).toString())
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


        binding.outputTextViewApps.movementMethod = ScrollingMovementMethod()
        binding.outputTextViewApps.text = listOfInstalledNames.toString()

        // get Battery Intent
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
        binding.outputTextBattery.text = batteryPct.toString()

        //get Memory Info RAM
        val availableMemory = getAvailableMemory().availMem / 1024
        val totalMemory = getAvailableMemory().totalMem / 1024
        val usedMemory = totalMemory - availableMemory

        binding.outputTextUsedMemory.text = "$availableMemory KB disponible, $totalMemory KB total, $usedMemory KB utilizado"

        // free space
        val freeInternalMemory = Environment.getDataDirectory()
        val freeExternalMemory = Environment.getDataDirectory().freeSpace
        val freeSpace = Environment.getRootDirectory().freeSpace
        //val totalSpace = Environment.getRootDirectory().totalSpace
        val totalFreeSpace = freeExternalMemory + freeSpace

        binding.outputTextFreeSpace.text = "$totalFreeSpace KB libres "



        // start your next activity
        foregroundOnlyUsageStatsButton.setOnClickListener{
            onClickStartSettings(view)
        }

        // get Usage for installed apps
        val statsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
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

                    usageList.add("${packageManager.getApplicationLabel(usageApp).toString()}" +
                            " ha usado: " + "%.2f".format(hours) +  " horas ")
                }
            }
        }
        if (usageList.isNullOrEmpty()){
            binding.outputTextViewUsage.text = "Debe habilitar permiso de uso para funcionamiento central"
        }else{
            binding.outputTextViewUsage.text = usageList.toString()
        }




    }

    override fun onStart() {
        super.onStart()

        updateButtonState(
            sharedPreferences.getBoolean(SharedPreferenceUtil.KEY_FOREGROUND_ENABLED, false)
        )
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        val serviceIntent = Intent(this, ForegroundOnlyLocationService::class.java)
        bindService(serviceIntent, foregroundOnlyServiceConnection, Context.BIND_AUTO_CREATE)


    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            foregroundOnlyBroadcastReceiver,
            IntentFilter(
                ForegroundOnlyLocationService.ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST)
        )

    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
            foregroundOnlyBroadcastReceiver
        )
        super.onPause()
    }

    override fun onStop() {
        if (foregroundOnlyLocationServiceBound) {
            unbindService(foregroundOnlyServiceConnection)
            foregroundOnlyLocationServiceBound = false
        }
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)

        super.onStop()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        // Updates button states if new while in use location is added to SharedPreferences.
        if (key == SharedPreferenceUtil.KEY_FOREGROUND_ENABLED) {
            updateButtonState(sharedPreferences.getBoolean(
                SharedPreferenceUtil.KEY_FOREGROUND_ENABLED, false)
            )
        }
    }


    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Log.i("Permission: ", "Granted")
            } else {
                Log.i("Permission: ", "Denied")
            }
        }

    // Step 1.0, Review Permissions: Method checks if permissions approved.
    private fun foregroundPermissionApproved(): Boolean {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun foregroundUsageStatsPermissionApproved(): Boolean {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.PACKAGE_USAGE_STATS
        )
    }

    // Step 1.0, Review Permissions: Method requests permissions.
    private fun requestForegroundPermissions() {
        val provideRationale = foregroundPermissionApproved()

        // If the user denied a previous request, but didn't check "Don't ask again", provide
        // additional rationale.
        if (provideRationale) {
            Snackbar.make(
                binding.mainLayout,
                R.string.permission_rationale,
                Snackbar.LENGTH_LONG
            )
                .setAction(R.string.ok) {
                    // Request permission
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
                    )
                }
                .show()
        } else {
            Log.d(TAG, "Request foreground only permission")
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun requestForegroundUsageStatsPermissions() {
        val provideRationale = foregroundPermissionApproved()

        // If the user denied a previous request, but didn't check "Don't ask again", provide
        // additional rationale.
        if (provideRationale) {
            Snackbar.make(
                binding.mainLayout,
                R.string.permission_rationale,
                Snackbar.LENGTH_LONG
            )
                .setAction(R.string.ok) {
                    // Request permission
                    ActivityCompat.requestPermissions(
                        this@MainActivity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
                    )
                }
                .show()
        } else {
            Log.d(TAG, "Request foreground only permission")
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
            )
        }
    }


    // Review Permissions: Handles permission result.
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionResult")

        when (requestCode) {
            REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE -> when {
                grantResults.isEmpty() ->
                    // If user interaction was interrupted, the permission request
                    // is cancelled and you receive empty arrays.
                    Log.d(TAG, "User interaction was cancelled.")

                grantResults[0] == PackageManager.PERMISSION_GRANTED ->
                    // Permission was granted.
                    foregroundOnlyLocationService?.subscribeToLocationUpdates()

                else -> {
                    // Permission denied.
                    updateButtonState(false)

                    Snackbar.make(
                        binding.mainLayout,
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_LONG
                    )
                        .setAction(R.string.settings) {
                            // Build intent that displays the App settings screen.
                            val intent = Intent()
                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            val uri = Uri.fromParts(
                                "package",
                                BuildConfig.APPLICATION_ID,
                                null
                            )
                            intent.data = uri
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        }
                        .show()
                }
            }
        }
    }

    private fun updateButtonState(trackingLocation: Boolean) {
        if (trackingLocation) {
            foregroundOnlyLocationButton.text = getString(R.string.stop_location_updates_button_text)
        } else {
            foregroundOnlyLocationButton.text = getString(R.string.start_location_updates_button_text)
        }
    }

    private fun updateUsageStatsButtonState(trackingLocation: Boolean) {
        if (trackingLocation) {
            foregroundOnlyLocationButton.text = getString(R.string.stop_location_updates_button_text)
        } else {
            foregroundOnlyLocationButton.text = getString(R.string.start_location_updates_button_text)
        }
    }

    fun onClickRequestPermission(view: View) {
        layout.showSnackbar(view,"Necesita hacer login",Snackbar.LENGTH_INDEFINITE,null){}
    }

    private fun logResultsToScreen(output: String) {
        val outputWithPreviousLogs = "$output\n${outputTextView.text}"
        outputTextView.text = outputWithPreviousLogs
    }


    /**
     * Receiver for location broadcasts from [ForegroundOnlyLocationService].
     */
    private inner class ForegroundOnlyBroadcastReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra<Location>(
                ForegroundOnlyLocationService.EXTRA_LOCATION
            )

            if (location != null) {
                logResultsToScreen("Foreground location: ${location.toText()}")
            }
        }
    }

    private fun getAvailableMemory(): ActivityManager.MemoryInfo {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also { memoryInfo ->
            activityManager.getMemoryInfo(memoryInfo)
        }
    }

    private fun onClickStartSettings(view: View) {
        Log.d(TAG,"Requesting Setting")
        // Do something in response to button
        try{
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
            }catch (e: Exception){
                Log.e(TAG,e.localizedMessage + e.message)
            }
        Log.d(TAG,"Requesting Setting requested")
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

