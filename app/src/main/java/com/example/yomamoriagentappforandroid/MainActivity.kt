//Deprecationのアラートを消す
@file:Suppress("DEPRECATION")

package com.example.yomamoriagentappforandroid

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.yomamoriagentappforandroid.ui.theme.YomamoriAgentAppForAndroidTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import java.lang.Exception
import java.net.URL
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

lateinit var locationUserId:String


class MainActivity : ComponentActivity() {
    //位置情報サービスクライアントの定義
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    //位置情報を受け取るためのコールバックの宣言
    private lateinit var locationCallback: LocationCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        confirmPermissionFirst()

        val intent = Intent(this, LocationService::class.java)
        startForegroundService(intent)

        setContent {
            YomamoriAgentAppForAndroidTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    yomamoriMainView(this)
                }
            }
        }
    }

    //permission確認用の関数
    private fun confirmPermissionFirst() {
        val launcher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                it
                val sms = it[Manifest.permission.READ_SMS] ?: false
                val phoneNumber = it[Manifest.permission.READ_PHONE_NUMBERS] ?: false
                val phoneState = it[Manifest.permission.READ_PHONE_STATE] ?: false
                val readContacts = it[Manifest.permission.READ_CONTACTS] ?: false
                val callLog = it[Manifest.permission.READ_CALL_LOG] ?: false
                val coarseLocation = it[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                val fineLocation = it[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                val postNotification = it[Manifest.permission.POST_NOTIFICATIONS] ?: false



                if (sms && phoneNumber && phoneState && readContacts && callLog &&
                    coarseLocation && fineLocation && postNotification ) {
                        val tm: TelephonyManager =
                            this.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

                        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PERMISSION_GRANTED) {
                            locationUserId = try {
                                tm.line1Number
                            } catch (e: SecurityException) {
                                error(e)
                            }
                        }
                } else {
                    val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                    builder
                        .setTitle("YOMAMORIを使いこなそう！！")
                        .setMessage(
                            "①「OK」ボタンを押す\n" +
                                    "②「権限」を押す\n" +
                                    "③「許可しない」の下にある項目すべてを「常に許可する」か「許可する」を選択する"
                        )
                        .setNegativeButton(android.R.string.cancel,
                            DialogInterface.OnClickListener { dialog, id ->
                                //キャンセルの時の処理
                                Toast.makeText(
                                    this,
                                    "!!YOMAMORIの機能が制限されます!!",
                                    Toast.LENGTH_LONG
                                ).show()
                            })
                        .setPositiveButton(android.R.string.ok,
                            DialogInterface.OnClickListener { dialog, id ->
                                //OKのときの処理
                                openSettings()
                                finish()
                            })
                        .show()

                }
            }
        //確認処理開始
        launcher.launch(
            arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_PHONE_NUMBERS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )
    }
    //設定画面への遷移用の関数
    fun openSettings(context: Context = this) {
        val uriString = "package:" + context.packageName
        val settingsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse(uriString)
        )
        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(settingsIntent)
    }


    //YOMAMORIに利用する機能クラス群
    //レシーバクラス（PhoneCallReceiver(BroadcastReceiver) がインテントブロードキャストを受信する）
    class PhoneCallReceiver : BroadcastReceiver() {
        //ブロードキャスト受信時の処理実装
        override fun onReceive(context: Context, intent: Intent) {
            //TelephonyManagerの生成
            val tm: TelephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            // リスナーの登録
            val yomamoriPhoneStateListener = CustomPhoneStateListener(tm, context)
            if (intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)) {
                tm.listen(yomamoriPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            }
            //リスナーの解除
            tm.listen(yomamoriPhoneStateListener, PhoneStateListener.LISTEN_NONE)
        }

        //リスナークラス
        class CustomPhoneStateListener(telephonyManager: TelephonyManager, context: Context) :
            PhoneStateListener() {
            //変数準備
            val TAG = CustomPhoneStateListener::class.java.simpleName
            val userId: String = try {
                telephonyManager.line1Number
            } catch (e: SecurityException) {
                error(e)
            }
            val context = context

            //電話の状態が変わった時の処理
            override fun onCallStateChanged(state: Int, incomingNumber: String) {
                Log.d(TAG, "Listener state:$state-PhoneNumber:$incomingNumber")
                when (state) {
                    //待ち受け（終了時）または通話状態
                    TelephonyManager.CALL_STATE_IDLE, TelephonyManager.CALL_STATE_OFFHOOK -> {}
                    //着信
                    TelephonyManager.CALL_STATE_RINGING -> {
                        if (incomingNumber != null) {
                            val customContentReader = CustomContentReader(incomingNumber)
                            val phoneCursor = customContentReader.queryPhoneList(context)
                            if (customContentReader.verifyQueryResult(phoneCursor) == 1) {
                                postIncomingNumber(userId, incomingNumber)
                            }
                        }
                    }
                }
            }

            //着信番号のpostリクエスト関数
            private fun postIncomingNumber(userId: String, incomingNumber: String) {
                //各種変数
                val userId = userId.trim()
                val timeStampFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS")
                val timeStamp = timeStampFormat.format(ZonedDateTime.now(ZoneId.of("Asia/Tokyo")))
                val incomingNumber = incomingNumber

                thread {
                    //URL指定
                    val url =
                        URL("https://w7cbqepn6a.execute-api.ap-northeast-1.amazonaws.com/yomamori/incomingdetection?userId=$userId&timeStamp=$timeStamp&incomingNumber=$incomingNumber")
                    val connection = url.openConnection() as HttpsURLConnection

                    try {
                        //接続
                        connection.requestMethod = "POST"
                        connection.doOutput = false
                        connection.setRequestProperty(
                            "Content-type",
                            "application/json; charset=utf-8"
                        )
                        connection.connect()

                        //レスポンス確認
                        if (connection.responseCode != HttpsURLConnection.HTTP_OK) {
                            //接続エラー
                            return@thread
                        }

                    } catch (e: Exception) {
                        error(e)
                    }
                }
            }
        }
    }
}

@Composable
fun yomamoriMainView(context: Context) {
    Column(
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Image(
            painter = painterResource(id = R.drawable.yomamori_title),
            contentDescription = null,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var buttonPressDetector by remember { mutableStateOf(false) }
        Button(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(180.dp, 100.dp),
            shape = MaterialTheme.shapes.small,

            onClick = {
                val uri = Uri.parse("https://developer.android.com/?hl=ja")
                val intentForTopPage = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(intentForTopPage)
                }) {
            Text(
                text = "YOMAMORI\n<Webページ>",
                fontSize = 20.sp
            )

        }
    }
}




@Preview(showBackground = true, showSystemUi = true)
@Composable
fun GreetingPreview() {
    YomamoriAgentAppForAndroidTheme {
        //yomamoriMainView(context)
    }
}