package com.cmouse.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cmouse.app.data.SettingsStore
import com.cmouse.app.hid.HidDeviceManager
import com.cmouse.app.hid.HidService
import com.cmouse.app.input.InputDispatcher
import com.cmouse.app.transport.LanClient
import android.app.AlertDialog
import com.cmouse.app.ui.CMouseTheme
import com.cmouse.app.ui.PadUiHolder
import com.cmouse.app.ui.setupLegacyUi
import com.cmouse.app.ui.KeyboardScreen
import com.cmouse.app.ui.SettingsSheet
import com.cmouse.app.ui.TrackpadScreen
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var settings: SettingsStore
    private lateinit var dispatcher: InputDispatcher

    private var lanClient: LanClient? = null
    private var hidService: HidService? = null
    private var hidBound = false

    // UI 状态
    private var hidRegistered by mutableStateOf(false)
    private var hostName by mutableStateOf<String?>(null)
    private var hidMsg by mutableStateOf("")
    private var lanState by mutableStateOf(LanClient.State.CLOSED)
    private var lanMsg by mutableStateOf("")
    private var statusVersion by mutableStateOf(0) // 触发设置页设备列表刷新
    private var crashReport by mutableStateOf<String?>(null)
    private var padUiHolder: PadUiHolder? = null

    internal fun currentStatus(): ConnectionStatus = ConnectionStatus(
        hidRegistered = hidRegistered,
        hostName = hostName,
        hidMsg = hidMsg,
        lanState = lanState,
        lanMsg = lanMsg,
        version = statusVersion,
        crashReport = crashReport
    )

    /** 传统 View 界面（Android 8 以下）的状态胶囊刷新。 */
    internal fun refreshLegacyStatus() {
        padUiHolder?.updateStatusPill(currentStatus())
    }

    private val statusRelay = object : HidDeviceManager.Listener {
        override fun onRegistered(registered: Boolean) {
            if (registered) hidMsg = ""
            hidRegistered = registered
            statusVersion++
            refreshLegacyStatus()
        }

        override fun onHostChanged(name: String?, connected: Boolean) {
            hostName = if (connected) name else null
            statusVersion++
            refreshLegacyStatus()
        }

        override fun onError(message: String) {
            hidMsg = message
        }
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            hidService = (service as HidService.LocalBinder).service
            hidService?.statusListener = statusRelay
            // 服务可能先于绑定运行并产生错误，此处补发
            hidService?.lastError?.let { statusRelay.onError(it) }
            hidRegistered = hidService?.hid?.isRegistered ?: false
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            hidService = null
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) doStartHid()
            else hidMsg = "蓝牙权限被拒绝，无法注册触控板设备"
        }

    private fun missingPermissions(): List<String> {
        val wanted = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                wanted += Manifest.permission.BLUETOOTH_CONNECT
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)
                wanted += Manifest.permission.BLUETOOTH_ADVERTISE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) wanted += Manifest.permission.POST_NOTIFICATIONS
        return wanted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashReporter()
        settings = SettingsStore(this)
        dispatcher = InputDispatcher(settings)
        dispatcher.hidProvider = { hidService?.hid }
        dispatcher.lanProvider = { lanClient }
        // 手势动作诊断提示：区分"手势未触发"与"动作未生效"
        dispatcher.onActionSent = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
        crashReport = readLastCrash()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Android 7.x 及以下：完全绕开 Compose——部分老 ROM 的 interop
            // 触摸分发不可靠（OPPO R9st 实测），主界面/设置/键盘全部走传统 View
            padUiHolder = setupLegacyUi(this, settings, dispatcher)
            crashReport?.let {
                AlertDialog.Builder(this)
                    .setTitle("上次异常退出报告")
                    .setMessage(it)
                    .setPositiveButton("知道了", null)
                    .show()
            }
        } else {
            setContent {
                CMouseTheme {
                    MainScaffold()
                }
            }
        }
    }

    /** 黑匣子：任何未捕获异常写入本地文件，下次启动展示给用户。 */
    private fun installCrashReporter() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                File(filesDir, "last_crash.txt").writeText(
                    "时间: $time\n" +
                        "设备: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}" +
                        " (API ${Build.VERSION.SDK_INT})\n" +
                        "线程: ${t.name}\n" +
                        Log.getStackTraceString(e)
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(t, e)
        }
    }

    private fun readLastCrash(): String? = try {
        val f = File(filesDir, "last_crash.txt")
        if (f.exists()) f.readText().take(1500) else null
    } catch (_: Throwable) {
        null
    }

    /**
     * 启动 HID：先确保权限齐备（授权回调里继续），再启动前台服务并请求"可被发现"。
     * 旧实现里授权是异步的而服务立即启动，registerApp 会因权限不足失败。
     */
    fun startHid() {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
            return
        }
        doStartHid()
    }

    private fun doStartHid() {
        // Android 9 以下不存在 BluetoothHidDevice 相关系统类，
        // 不能启动 HID 服务（连构造都不行，会 NoClassDefFoundError）
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            hidMsg = "蓝牙直连模式需要 Android 9.0 以上（当前 Android ${Build.VERSION.RELEASE}），请改用 Wi-Fi 接收端模式"
            return
        }
        // 启动链路上的任何异常都转为界面提示，避免无信息闪退
        try {
            val intent = Intent(this, HidService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
            else startService(intent)
            if (!hidBound) {
                bindService(intent, conn, Context.BIND_AUTO_CREATE)
                hidBound = true
            }
            // 注册后手机需处于可被发现状态，电脑才能在蓝牙设置中看到它
            val adapter = getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter != null && adapter.isEnabled &&
                adapter.scanMode != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
            ) {
                val d = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                d.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
                try {
                    startActivity(d)
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            hidMsg = "启动失败：${e.javaClass.simpleName}: ${e.message ?: ""}"
        }
    }

    fun stopHid() {
        if (hidBound) {
            unbindService(conn)
            hidBound = false
        }
        stopService(Intent(this, HidService::class.java))
        hidService = null
        hidRegistered = false
    }

    fun connectLan(host: String, port: Int, code: String) {
        disconnectLan()
        val name = Build.MODEL ?: "cMouse"
        val client = LanClient(host, port, name, object : LanClient.Listener {
            override fun onState(state: LanClient.State, message: String) {
                lanState = state
                if (message.isNotEmpty()) lanMsg = message
                if (state == LanClient.State.READY) {
                    settings.rememberDevice("$host:$port", host, "lan")
                    statusVersion++
                }
            }
        })
        lanClient = client
        client.start(code)
    }

    fun disconnectLan() {
        lanClient?.stop()
        lanClient = null
        lanState = LanClient.State.CLOSED
    }

    val lanConnected: Boolean get() = lanClient?.isReady == true

    override fun onDestroy() {
        if (hidBound) {
            unbindService(conn)
            hidBound = false
        }
        lanClient?.stop()
        super.onDestroy()
    }

    @androidx.compose.runtime.Composable
    private fun MainScaffold() {
        var showKeyboard by mutableStateOf(false)
        var showSettings by mutableStateOf(false)
        Box(Modifier.fillMaxSize()) {
            val status = ConnectionStatus(
                hidRegistered = hidRegistered,
                hostName = hostName,
                hidMsg = hidMsg,
                lanState = lanState,
                lanMsg = lanMsg,
                version = statusVersion,
                crashReport = crashReport
            )
            TrackpadScreen(
                modifier = Modifier.fillMaxSize(),
                settings = settings,
                dispatcher = dispatcher,
                activity = this@MainActivity,
                status = status,
                onOpenKeyboard = { showKeyboard = true },
                onOpenSettings = { showSettings = true },
                onDismissCrash = { crashReport = null }
            )
            if (showKeyboard) {
                KeyboardScreen(
                    modifier = Modifier.fillMaxSize(),
                    dispatcher = dispatcher,
                    lanMode = settings.getString(SettingsStore.Keys.MODE, "hid") == "lan",
                    onDismiss = { showKeyboard = false }
                )
            }
            if (showSettings) {
                SettingsSheet(
                    status = status,
                    settings = settings,
                    activity = this@MainActivity,
                    dispatcher = dispatcher,
                    onDismiss = { showSettings = false }
                )
            }
        }
    }
}

/** 传给各界面的连接状态快照。 */
data class ConnectionStatus(
    val hidRegistered: Boolean,
    val hostName: String?,
    val hidMsg: String,
    val lanState: LanClient.State,
    val lanMsg: String,
    val version: Int,
    val crashReport: String? = null
)
