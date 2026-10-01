package com.cmouse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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

/**
 * 设置抽屉（参考设计稿）：从触控板页右下角呼出，
 * 两个标签页——"光标与点按" / "更多手势"。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    status: ConnectionStatus,
    settings: SettingsStore,
    activity: MainActivity,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableStateOf(0) }
    var mode by remember { mutableStateOf(settings.getString(SettingsStore.Keys.MODE, "hid")) }
    var sensitivity by remember { mutableStateOf(settings.getFloat(SettingsStore.Keys.SENSITIVITY, 1f)) }
    var scrollSpeed by remember { mutableStateOf(settings.getFloat(SettingsStore.Keys.SCROLL_SPEED, 1f)) }
    var pinch by remember { mutableStateOf(settings.getBool(SettingsStore.Keys.PINCH_ZOOM, true)) }
    var natural by remember { mutableStateOf(settings.getBool(SettingsStore.Keys.NATURAL_SCROLL, true)) }
    var lanHost by remember { mutableStateOf(settings.getString(SettingsStore.Keys.LAN_HOST)) }
    var lanPort by remember { mutableStateOf(settings.getString(SettingsStore.Keys.LAN_PORT, "8433")) }
    var lanCode by remember { mutableStateOf(settings.getString(SettingsStore.Keys.LAN_CODE)) }
    var gestureDialog by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var version by remember(status.version) { mutableStateOf(status.version) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text("设置", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            TabRow(selectedTabIndex = tab) {
                Tab(text = { Text("光标与点按") }, selected = tab == 0, onClick = { tab = 0 })
                Tab(text = { Text("更多手势") }, selected = tab == 1, onClick = { tab = 1 })
            }
            Spacer(Modifier.height(12.dp))

            if (tab == 0) {
                // ---- 连接模式 ----
                SectionCard(title = "连接模式") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = mode == "hid",
                            onClick = {
                                mode = "hid"; settings.putString(SettingsStore.Keys.MODE, "hid")
                                activity.stopHid(); activity.disconnectLan()
                            },
                            label = { Text("蓝牙直连（电脑免安装）") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        FilterChip(
                            selected = mode == "lan",
                            onClick = {
                                mode = "lan"; settings.putString(SettingsStore.Keys.MODE, "lan")
                                activity.stopHid()
                            },
                            label = { Text("Wi-Fi 接收端") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (mode == "hid")
                            "电脑端零安装：手机注册为标准蓝牙键鼠，在电脑蓝牙设置中配对即可。" +
                                "需 Android 9.0+；iPhone 不支持本模式（平台限制）。"
                        else
                            "需在电脑端运行 cMouse Receiver（无驱动、单文件）。iPhone 仅支持此模式。",
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
                                ).width(10.dp).height(10.dp)
                            ) { }
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
                        if (status.hidMsg.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                status.hidMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "配对步骤：点\"启动\"后，在弹窗中允许手机\"对附近蓝牙设备可见\"，" +
                                "再到电脑蓝牙设置中找到 cMouse Trackpad 并配对。",
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

                // ---- 触控手感 ----
                SectionCard(title = "触控手感") {
                    SettingSlider("光标灵敏度", sensitivity, 0.5f, 3f) {
                        sensitivity = it; settings.putFloat(SettingsStore.Keys.SENSITIVITY, it)
                    }
                    SettingSlider("滚动速度", scrollSpeed, 0.3f, 3f) {
                        scrollSpeed = it; settings.putFloat(SettingsStore.Keys.SCROLL_SPEED, it)
                    }
                    SwitchRow("自然滚动（内容跟随手指）", natural) {
                        natural = it; settings.putBool(SettingsStore.Keys.NATURAL_SCROLL, it)
                    }
                    SwitchRow("双指捏合缩放（Ctrl+滚轮）", pinch) {
                        pinch = it; settings.putBool(SettingsStore.Keys.PINCH_ZOOM, it)
                    }
                }
            } else {
                // ---- 自定义手势 ----
                SectionCard(title = "三指滑动（自定义映射）") {
                    GestureRow("上滑 · 调度中心 / 任务视图", "three_up", settings) { gestureDialog = "three_up" }
                    GestureRow("下滑 · 显示桌面", "three_down", settings) { gestureDialog = "three_down" }
                    GestureRow("左滑 · 切换到左侧屏幕", "three_left", settings) { gestureDialog = "three_left" }
                    GestureRow("右滑 · 切换到右侧屏幕", "three_right", settings) { gestureDialog = "three_right" }
                }
                SectionCard(title = "手势说明") {
                    Text(
                        "单指：移动 · 轻点=左键 · 双击=双击 · 长按=拖拽\n" +
                            "双指：滑动=滚动 · 轻点=右键 · 捏合=缩放\n" +
                            "三指：按上表映射发送组合键（触发时手机会震动并有提示）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---- 数据与隐私 ----
            SectionCard(title = "数据与隐私") {
                Text(
                    "所有数据（配置、手势映射、配对设备）仅保存在本机 SQLite（cmouse.db），不采集、不上传。" +
                        "卸载 App 时系统会自动彻底清除以上全部数据，无需手动清理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { confirmClear = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("清除所有本地数据") }
                if (confirmClear) {
                    AlertDialog(
                        onDismissRequest = { confirmClear = false },
                        title = { Text("清除所有本地数据？") },
                        text = {
                            Text(
                                "将删除全部配置、手势映射与配对设备记录，并恢复默认设置。" +
                                    "连接状态会断开，需重新配置。"
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                activity.disconnectLan()
                                activity.stopHid()
                                settings.resetAll()
                                mode = "hid"
                                sensitivity = 1f
                                scrollSpeed = 1f
                                pinch = true
                                natural = true
                                lanHost = ""
                                lanPort = "8433"
                                lanCode = ""
                                confirmClear = false
                                version++
                            }) { Text("清除") }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmClear = false }) { Text("取消") }
                        }
                    )
                }
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

            Spacer(Modifier.height(28.dp))
        }
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
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
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
    var expanded by remember { mutableStateOf(true) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "收起$title" else "展开$title"
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                content()
            }
        }
    }
}
