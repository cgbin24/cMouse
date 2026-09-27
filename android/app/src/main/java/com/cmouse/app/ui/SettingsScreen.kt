package com.cmouse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.cmouse.app.ConnectionStatus
import com.cmouse.app.MainActivity
import com.cmouse.app.data.SettingsStore
import com.cmouse.app.transport.LanClient

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    settings: SettingsStore,
    activity: MainActivity,
    status: ConnectionStatus
) {
    var mode by remember { mutableStateOf(settings.getString(SettingsStore.Keys.MODE, "hid")) }
    var sensitivity by remember { mutableStateOf(settings.getFloat(SettingsStore.Keys.SENSITIVITY, 1f)) }
    var scrollSpeed by remember { mutableStateOf(settings.getFloat(SettingsStore.Keys.SCROLL_SPEED, 1f)) }
    var pinch by remember { mutableStateOf(settings.getBool(SettingsStore.Keys.PINCH_ZOOM, true)) }
    var lanHost by remember { mutableStateOf(settings.getString(SettingsStore.Keys.LAN_HOST)) }
    var lanPort by remember { mutableStateOf(settings.getString(SettingsStore.Keys.LAN_PORT, "8433")) }
    var lanCode by remember { mutableStateOf(settings.getString(SettingsStore.Keys.LAN_CODE)) }
    var gestureDialog by remember { mutableStateOf<String?>(null) }
    var version by remember(status.version) { mutableStateOf(status.version) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)

        // ---- 连接模式 ----
        SectionCard(title = "连接模式") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = mode == "hid",
                    onClick = {
                        mode = "hid"; settings.putString(SettingsStore.Keys.MODE, "hid")
                        activity.stopHid(); activity.disconnectLan()
                    },
                    label = { Text("蓝牙直连（电脑免安装）") }
                )
                FilterChip(
                    selected = mode == "lan",
                    onClick = {
                        mode = "lan"; settings.putString(SettingsStore.Keys.MODE, "lan")
                        activity.stopHid()
                    },
                    label = { Text("Wi-Fi 接收端") }
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (mode == "hid")
                    "电脑端零安装：手机注册为标准蓝牙键鼠，在电脑蓝牙设置中配对即可。" +
                        "iPhone 不支持本模式（平台限制）。"
                else
                    "需在电脑端运行 cMouse Receiver（无驱动、单文件、约 1–2 MB）。" +
                        "iPhone 仅支持此模式。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- 当前模式配置 ----
        if (mode == "hid") {
            SectionCard(title = "蓝牙连接") {
                val registered = status.hidRegistered
                val connected = registered && status.hostName != null
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.padding(end = 10.dp).clip(CircleShape).background(
                            if (connected) Accent else MaterialTheme.colorScheme.onSurfaceVariant
                        ).then(Modifier).width(10.dp).height(10.dp)
                    )
                    Text(
                        when {
                            connected -> "已配对：${status.hostName}"
                            registered -> "已注册，等待电脑配对…"
                            else -> "未启动"
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { activity.startHid() }) { Text("启动并等待配对") }
                    OutlinedButton(onClick = { activity.stopHid() }) { Text("停止") }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "配对步骤：点\"启动\"后，在电脑的蓝牙设置中找到名为 cMouse Trackpad 的设备并配对。" +
                        "首次启动会请求蓝牙权限与\"可被发现\"。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            SectionCard(title = "接收端连接") {
                val ready = status.lanState == LanClient.State.READY
                OutlinedTextField(
                    value = lanHost, onValueChange = { lanHost = it },
                    label = { Text("电脑 IP（接收端菜单栏可见）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = lanPort, onValueChange = { lanPort = it.filter { c -> c.isDigit() } },
                        label = { Text("端口") }, singleLine = true,
                        modifier = Modifier.width(140.dp)
                    )
                    OutlinedTextField(
                        value = lanCode, onValueChange = { lanCode = it },
                        label = { Text("配对码") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = {
                        settings.putString(SettingsStore.Keys.LAN_HOST, lanHost)
                        settings.putString(SettingsStore.Keys.LAN_PORT, lanPort)
                        settings.putString(SettingsStore.Keys.LAN_CODE, lanCode)
                        activity.connectLan(lanHost, lanPort.toIntOrNull() ?: 8433, lanCode)
                    }) { Text(if (ready) "重连" else "连接") }
                    OutlinedButton(onClick = { activity.disconnectLan() }) { Text("断开") }
                    Text(
                        when (status.lanState) {
                            LanClient.State.READY -> "已连接"
                            LanClient.State.CONNECTING, LanClient.State.PAIRING -> "连接中…"
                            LanClient.State.ERROR -> status.lanMsg
                            else -> "未连接"
                        },
                        modifier = Modifier.align(Alignment.CenterVertically),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ---- 手感 ----
        SectionCard(title = "触控手感") {
            SettingSlider("光标灵敏度", sensitivity, 0.5f, 3f) {
                sensitivity = it; settings.putFloat(SettingsStore.Keys.SENSITIVITY, it)
            }
            SettingSlider("滚动速度", scrollSpeed, 0.3f, 3f) {
                scrollSpeed = it; settings.putFloat(SettingsStore.Keys.SCROLL_SPEED, it)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("双指捏合缩放（Ctrl+滚轮）")
                Spacer(Modifier.weight(1f))
                Switch(checked = pinch, onCheckedChange = {
                    pinch = it; settings.putBool(SettingsStore.Keys.PINCH_ZOOM, it)
                })
            }
        }

        // ---- 自定义手势 ----
        SectionCard(title = "三指滑动（自定义映射）") {
            GestureRow("上滑", "three_up", settings) { gestureDialog = "three_up" }
            GestureRow("下滑", "three_down", settings) { gestureDialog = "three_down" }
            GestureRow("左滑", "three_left", settings) { gestureDialog = "three_left" }
            GestureRow("右滑", "three_right", settings) { gestureDialog = "three_right" }
        }

        // ---- 数据与隐私 ----
        SectionCard(title = "数据与隐私") {
            Text(
                "所有数据（配置、手势映射、配对设备）仅保存在本机 SQLite（cmouse.db），不采集、不上传。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            version // 触发重建以刷新设备列表
            val devices = settings.devices()
            if (devices.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                devices.forEach { d ->
                    Text(
                        "· ${d.second}（${if (d.third == "hid") "蓝牙" else "Wi-Fi"}）",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }

    gestureDialog?.let { gesture ->
        AlertDialog(
            onDismissRequest = { gestureDialog = null },
            title = { Text("选择动作") },
            text = {
                Column {
                    ComboOption("无动作", "none", settings, gesture) { gestureDialog = null }
                    ComboOption("调度中心 / 任务视图", "ctrl+up", settings, gesture) { gestureDialog = null }
                    ComboOption("应用窗口切换", "ctrl+left", settings, gesture) { gestureDialog = null }
                    ComboOption("显示桌面", "win+d", settings, gesture) { gestureDialog = null }
                    ComboOption("启动台 / 开始菜单", "win", settings, gesture) { gestureDialog = null }
                    ComboOption("锁屏", "cmd+ctrl+q", settings, gesture) { gestureDialog = null }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun ComboOption(
    label: String,
    combo: String,
    settings: SettingsStore,
    gesture: String,
    onDone: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable {
            settings.setGestureAction(gesture, "combo", combo)
            onDone()
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = settings.gestureAction(gesture)?.second == combo ||
                (settings.gestureAction(gesture)?.first == "none" && combo == "none"),
            onClick = {
                if (combo == "none") settings.setGestureAction(gesture, "none", "")
                else settings.setGestureAction(gesture, "combo", combo)
                onDone()
            }
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GestureRow(label: String, gesture: String, settings: SettingsStore, onEdit: () -> Unit) {
    val action = settings.gestureAction(gesture)
    Row(Modifier.fillMaxWidth().clickable(onClick = onEdit), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Text(
            when {
                action == null || action.first == "none" -> "无动作"
                else -> action.second
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    from: Float,
    to: Float,
    onChange: (Float) -> Unit
) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                "%.1f×".format(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = from..to)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
