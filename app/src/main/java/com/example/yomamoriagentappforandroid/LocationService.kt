package com.example.yomamoriagentappforandroid

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import java.net.URL
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread


@Suppress("DEPRECATION")
class LocationService : Service() {
    companion object {
        const val CHANNEL_ID = "777"
    }
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        Log.d("確認","oncreate")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        //通知チャネル作成用の変数
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val name = "YOMAMORI"
        val id = "yomamroi_foreground"
        val notifyDescription = "Yomomariがサービス起動中"

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult ?: return
                postLocationInfo(
                    locationUserId,
                    locationResult.lastLocation?.latitude.toString(),
                    locationResult.lastLocation?.longitude.toString()
                )
            }
        }

        //通知用のチャネルの作成
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val yomamoriNotificationChannel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH)
            yomamoriNotificationChannel
            yomamoriNotificationChannel.apply {
                description = notifyDescription
                setSound(null,null)
                enableLights(false)
                enableVibration(false)
            }

            manager.createNotificationChannel(yomamoriNotificationChannel)
        }
        //通知情報
        val openIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Yomamori")
            .setContentText("みまもり中...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent)
            .build()

        startForeground(9999, notification)

        startLocationUpdates()

        Log.d("確認", "onStartCommand実行")

        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }


    private fun startLocationUpdates() {
        val locationRequest = createLocationRequest() ?: return
        try{
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                null)
        }catch (e:SecurityException){
            error(e)
        }

    }

    private fun createLocationRequest(): LocationRequest? {
        return LocationRequest.create()?.apply {
            interval = 900000
            fastestInterval = 900000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
    }


    //位置情報のpostリクエスト関数
    private fun postLocationInfo(userId: String, latitude: String, longitude: String){
        //各種変数
        val userId = userId
        Log.d("確認", userId)
        val timeStampFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS")
        val timeStamp = timeStampFormat.format(ZonedDateTime.now(ZoneId.of("Asia/Tokyo")))
        val latitude = latitude
        val longitude = longitude

        thread {
            //URL指定
            val url =
                URL("https://w7cbqepn6a.execute-api.ap-northeast-1.amazonaws.com/yomamori/locationposter?" +
                        "userId=$userId&timeStamp=$timeStamp&latitude=$latitude&longitude=$longitude")
            val connection = url.openConnection() as HttpsURLConnection

            try{
                //接続
                connection.requestMethod = "POST"
                connection.doOutput = false
                connection.setRequestProperty("Content-type", "application/json; charset=utf-8")
                connection.connect()

                //レスポンス確認
                if (connection.responseCode != HttpsURLConnection.HTTP_OK){
                    //接続エラー
                    return@thread
                }

            }catch(e: Exception){
                error(e)
            }
        }
    }


}