package com.cmouse.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cmouse.app.ConnectionStatus
import com.cmouse.app.MainActivity
import com.cmouse.app.data.SettingsStore
import com.cmouse.app.input.InputDispatcher
import com.cmouse.app.transport.LanClient

@Composable
fun TrackpadScreen(
    modifier: Modifier = Modifier,
    settings: SettingsStore,
    dispatcher: InputDispatcher,
    activity: MainActivity,
    status: ConnectionStatus,
    onOpenKeyboard: () -> Unit
) {
    val lanMode = settings.getString(SettingsStore.Keys.MODE, "hid") == "lan"
    val connected = if (lanMode) status.lanState == LanClient.State.READY
    else (status.hidRegistered && status.hostName != null)

    Column(modifier.padding(16.dp)) {
        // 状态卡
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(10.dp).clip(CircleShape).background(
                        if (connected) Accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = when {
                        lanMode && status.lanState == LanClient.State.READY ->
                            "已连接接收端 ${status.hostName ?: ""}"
                        lanMode && status.lanMsg.isNotEmpty() -> status.lanMsg
                        lanMode -> "Wi-Fi 模式：未连接"
                        !status.hidRegistered -> "蓝牙模式：未注册"
                        else -> "已配对：${status.hostName ?: ""}"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.weight(1f))
                if (lanMode) {
                    TextButton(onClick = {
                        if (activity.lanConnected) activity.disconnectLan()
                    }) { Text("断开") }
                } else {
                    TextButton(onClick = { activity.startHid() }) { Text("开始连接") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // 触控板表面
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx -> TrackpadView(ctx, settings, dispatcher) }
                )
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "单指移动 · 轻点单击\n双指滑动滚动 · 双指轻点右键\n双指捏合缩放 · 三指滑动多任务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AssistChip(
                onClick = onOpenKeyboard,
                label = { Text("⌨ 键盘") },
                modifier = Modifier.weight(1f)
            )
            AssistChip(
                onClick = { dispatcher.click(InputDispatcher.BUTTON_LEFT, false) },
                label = { Text("左键") },
                modifier = Modifier.weight(1f)
            )
            AssistChip(
                onClick = { dispatcher.click(InputDispatcher.BUTTON_RIGHT, false) },
                label = { Text("右键") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardScreen(
    modifier: Modifier = Modifier,
    dispatcher: InputDispatcher,
    lanMode: Boolean
) {
    var sticky by remember { mutableStateOf(setOf<String>()) }
    var textBuf by remember { mutableStateOf("") }

    fun pressChar(c: Char) {
        if (sticky.isEmpty()) dispatcher.keyChar(c)
        else dispatcher.keyChar(c, sticky)
        if (sticky.contains("shift")) sticky = sticky - "shift" else if (sticky.isNotEmpty()) sticky = emptySet()
    }

    fun pressCombo(combo: String) {
        val translated = when (combo) {
            "pgup" -> "pageup"; "pgdn" -> "pagedown"; "del" -> "delete"
            else -> combo
        }
        dispatcher.specialKey(if (sticky.isEmpty()) translated else (sticky + translated).joinToString("+"))
        sticky = emptySet()
    }

    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (lanMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = textBuf,
                    onValueChange = { textBuf = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入文本（直传接收端，支持中文）") },
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    if (textBuf.isNotEmpty()) {
                        dispatcher.text(textBuf)
                        textBuf = ""
                    }
                }) { Text("发送") }
            }
        }

        KeyRow(
            keys = listOf("esc", "tab", "home", "end", "pgup", "pgdn", "del")
        ) { pressCombo(it) }

        KeyRow(keys = ('1'..'9').map { it.toString() } + listOf("0", "-", "=")) {
            pressChar(it.first())
        }
        KeyRow(keys = "qwertyuiop".map { it.toString() }) { pressChar(it.first()) }
        KeyRow(keys = "asdfghjkl".map { it.toString() }) { pressChar(it.first()) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyCap("⇧", sticky.contains("shift")) {
                sticky = if (sticky.contains("shift")) sticky - "shift" else sticky + "shift"
            }
            KeyCap("z") { pressChar('z') }
            KeyCap("x") { pressChar('x') }
            KeyCap("c") { pressChar('c') }
            KeyCap("v") { pressChar('v') }
            KeyCap("b") { pressChar('b') }
            KeyCap("n") { pressChar('n') }
            KeyCap("m") { pressChar('m') }
            KeyCap(",", weight = 0.7f) { pressChar(',') }
            KeyCap(".", weight = 0.7f) { pressChar('.') }
            IconKeyCap(Icons.AutoMirrored.Outlined.Backspace) { pressCombo("backspace") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyCap("Ctrl", sticky.contains("ctrl")) {
                sticky = if (sticky.contains("ctrl")) sticky - "ctrl" else sticky + "ctrl"
            }
            KeyCap("⌥", sticky.contains("alt")) {
                sticky = if (sticky.contains("alt")) sticky - "alt" else sticky + "alt"
            }
            KeyCap("⌘", sticky.contains("cmd")) {
                sticky = if (sticky.contains("cmd")) sticky - "cmd" else sticky + "cmd"
            }
            KeyCap("space", weight = 2f) { pressChar(' ') }
            IconKeyCap(Icons.AutoMirrored.Outlined.KeyboardReturn) { pressCombo("enter") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.weight(1f))
            KeyCap("←", weight = 1f) { pressCombo("left") }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyCap("↑") { pressCombo("up") }
                KeyCap("↓") { pressCombo("down") }
            }
            KeyCap("→", weight = 1f) { pressCombo("right") }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun KeyRow(keys: List<String>, weight: Float = 1f, onPress: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { k ->
            KeyCap(k, weight = weight) { onPress(k) }
        }
    }
}

@Composable
private fun IconKeyCap(icon: androidx.compose.ui.graphics.vector.ImageVector, onPress: () -> Unit) {
    Surface(
        onClick = onPress,
        modifier = Modifier.weight(1.4f).height(44.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun KeyCap(label: String, active: Boolean = false, weight: Float = 1f, onPress: () -> Unit) {
    Surface(
        onClick = onPress,
        modifier = Modifier.weight(weight).height(44.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (active) Accent else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) androidx.compose.ui.graphics.Color.White
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
