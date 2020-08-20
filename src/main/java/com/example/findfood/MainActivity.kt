package com.example.findfood

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import com.bumptech.glide.Glide

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private lateinit var task: Task<LocationSettingsResponse>
    private lateinit var mGeoCoder: Geocoder
    private var lastLocation: Location? = null
    private var loading = true

    companion object {
        val TAG = "GoogleLocationServices :: "
        var latitude: Double = 0.0
        var longitude: Double = 0.0
        val REQUEST_PERMISSIONS_REQUEST_CODE = 10
        var area1Name:String? = null
        var area2Name:String? = null
        var area3Name:String? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageShow()

        //
        mGeoCoder = Geocoder(applicationContext, Locale.KOREAN)

        // 위치 설정 및 자동 업데이트
        val builder = LocationSettingsRequest.Builder()
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        task = client.checkLocationSettings(builder.build())

        task.addOnCompleteListener {
            if (it.isSuccessful) {
                val locationSettingsStates = task.result
                // 위치서비스가 설정이 안되어 있으면
                if (!locationSettingsStates!!.locationSettingsStates.isLocationUsable) {
                    Log.d(TAG, "lcationSettingsStates is disable")
                    userAddress.setText("위치정보 없음")
                    longLat.setText("$latitude, $longitude")
                    AlertDialog.Builder(this)
                        .setTitle("위치 정보 활성화")
                        .setMessage("위치 정보 활성화가 필요합니다.")
                        .setNeutralButton("설정", DialogInterface.OnClickListener { dialog, which ->
                            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            startActivity(intent)
                            startLocationUpdates("onCreate")
                        })
                        .setPositiveButton("거절", DialogInterface.OnClickListener { dialog, which ->
                            Log.d(TAG, "위치 설정 거부")
                            finish()
                        })
                        .setCancelable(false)
                        .create()
                        .show()
                } else {
                    getLastLocation()
                }
            } else {
                Log.d(TAG, "Fail to create task")
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        getLastLocation()

        locationRequest = LocationRequest.create()
        locationRequest.run {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 6000
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                if (longitude != 0.0 && latitude != 0.0) {
                    stopLocationUpdates()

                    // 검색 시작
                    // http 허용하기 - https://stackoverflow.com/questions/45940861/android-8-cleartext-http-traffic-not-permitted
                    val url = getString(R.string.url_haed) + "address?longitude=${longitude}&latitude=${latitude}"
                    Log.d("HTTP_URL :", url)
                    AsyncUrlCOnnect().execute(url)

                } else {
                    locationResult ?: return
                    Log.d(TAG, "콜백 시작")
                    Log.d(TAG, "locationResult - ${locationResult.locations}")
                    for (location in locationResult.locations) {
                        longitude = location.longitude
                        latitude = location.latitude
                    }
                    Log.d(TAG, "$latitude, $longitude")
                    userAddress!!.setText(locationToAddress())
                    longLat.setText("$latitude, $longitude")
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        ).addOnCompleteListener{
            Log.d(TAG, "qweqweqwe")
        }

        if(!loading){
            category1.setOnClickListener {
                val intent = Intent(this@MainActivity, LoadingPage::class.java)
                startActivity(intent)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!checkPermissions()) {
            requestPermissions()
        } else {
            getLastLocation()
        }
    }

    // 권한 확인
    private fun checkPermissions(): Boolean {
        val permissionState =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        return permissionState == PackageManager.PERMISSION_GRANTED
    }

    private fun startLocationPermissionRequest() {
        ActivityCompat.requestPermissions(
            this@MainActivity,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_PERMISSIONS_REQUEST_CODE
        )
    }

    private fun requestPermissions() {
        val shouldProvideRelationale = ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (shouldProvideRelationale) {
            startLocationPermissionRequest()
        } else {
            startLocationPermissionRequest()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        // 설정된 리퀘스트 코드로 확인한다.
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.size <= 0) {
                // 권한 요청 취소 됐을 때
                Log.i(TAG, "User interaction was cancelled.")
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation()
            } else {
                // 권한 요청 거절, 다시 요청
                startLocationPermissionRequest()
            }
        }
    }

    // 위치 자동 설정
    private fun getLastLocation() {
        fusedLocationClient!!.lastLocation
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful && task.result != null) {
                    lastLocation = task.result
                    // set lat & long
                    longitude = lastLocation!!.longitude
                    latitude = lastLocation!!.latitude
                    Log.d(TAG, "1")
                    userAddress!!.setText(locationToAddress())
                    longLat.setText("$latitude, $longitude")
                } else {
                    Log.d(TAG, "getLastLocaton:Exception")
                    startLocationUpdates("getLastLocation")
                }
            }
        fusedLocationClient.lastLocation
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates("onResume")
    }

    private fun startLocationUpdates(where: String) {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        Log.d(TAG, "function started at ${where}")
    }

    // 업데이트 중지
    private fun stopLocationUpdates() {
        Log.d(TAG, "콜백 종료")
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    // 위도경도 주소로 변경
    private fun locationToAddress(): String {
        var mResultList: List<Address>? = null
        if (longitude != 0.0 && latitude != 0.0) {
            try {
                mResultList = mGeoCoder.getFromLocation(
                    latitude, longitude, 1
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (mResultList != null) {
                Log.d(TAG, "CurrentLocation : ${mResultList!![0].getAddressLine((0))}")
            }
        }
        val address = mResultList!![0].getAddressLine((0))
        return address
    }

    inner class AsyncUrlCOnnect:AsyncTask<String, String, String>(){
        override fun doInBackground(vararg url: String?): String {
            var text:String
            val connection = URL(url[0]).openConnection() as HttpURLConnection
            try{
                connection.connect()
                text =
                    connection.inputStream.use { it.reader().use{reader -> reader.readText()} }
            }finally {
                connection.disconnect()
            }
            // text 에 response된 jsonString 저장
            return text
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            Log.d(TAG, "${result.toString()}")
            jsonHandler(result!!)
        }
    }
    private fun jsonHandler(jsonString:String){
        val jsonObject = JSONObject(jsonString)
        val returnText = jsonObject.getString("area1Name") + ", " +
        jsonObject.getString("area2Name") + ", " +
        jsonObject.getString("area3Name")
        area1Name = jsonObject.getString("area1Name")
        area2Name = jsonObject.getString("area2Name")
        area3Name = jsonObject.getString("area3Name")
        areaName.setText(returnText)
        invisibleLoading()
    }

    // image show
    private fun imageShow(){
        Glide.with(this).load(R.drawable.loading_gif).into(image_view)
    }

    private fun invisibleLoading(){
        loadingLayout.visibility = View.GONE
    }
}
