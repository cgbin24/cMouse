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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import android.widget.TextView
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.RectF
import com.cmouse.app.ConnectionStatus
import com.cmouse.app.MainActivity
import com.cmouse.app.data.SettingsStore
import com.cmouse.app.input.InputDispatcher
import com.cmouse.app.transport.LanClient

/**
 * 全屏触控板（参考设计稿）：整屏都是触控面，
 * 顶部状态胶囊 + 底部左/右键 + 左下键盘 / 右下设置两个悬浮圆钮。
 */
@Composable
fun TrackpadScreen(
    modifier: Modifier = Modifier,
    settings: SettingsStore,
    dispatcher: InputDispatcher,
    activity: MainActivity,
    status: ConnectionStatus,
    onOpenKeyboard: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissCrash: () -> Unit = {}
) {
    // 上次异常退出的崩溃报告（黑匣子）
    if (status.crashReport != null) {
        AlertDialog(
            onDismissRequest = onDismissCrash,
            title = { Text("上次异常退出报告") },
            text = {
                Column(
                    Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                ) {
                    Text(status.crashReport ?: "", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = onDismissCrash) { Text("知道了") } }
        )
    }

    // 触控板与悬浮控件全部在同一个原生容器内（View 体系保证按钮优先接收触摸），
    // Compose 侧只负责随状态刷新顶部胶囊文案。
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            buildPadUi(ctx, settings, dispatcher, onOpenKeyboard, onOpenSettings).root
        },
        update = { root ->
            (root.getChildAt(1) as? TextView)?.text = padStatusText(status, settings)
        }
    )
}

@Composable
fun KeyboardScreen(
    modifier: Modifier = Modifier,
    dispatcher: InputDispatcher,
    lanMode: Boolean,
    onDismiss: () -> Unit = {}
) {
    var sticky by remember { mutableStateOf(setOf<String>()) }
    var textBuf by remember { mutableStateOf("") }

    fun toggle(m: String) {
        sticky = if (sticky.contains(m)) sticky - m else sticky + m
    }

    fun pressChar(c: Char) {
        if (sticky.isEmpty()) dispatcher.keyChar(c) else dispatcher.keyChar(c, sticky)
        if (sticky.contains("shift")) sticky = sticky - "shift"
        else if (sticky.isNotEmpty()) sticky = emptySet()
    }

    fun pressCombo(combo: String) {
        val translated = when (combo) {
            "pgup" -> "pageup"; "pgdn" -> "pagedown"; "del" -> "delete"
            else -> combo
        }
        dispatcher.specialKey(if (sticky.isEmpty()) translated else (sticky + translated).joinToString("+"))
        sticky = emptySet()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 顶部：收起按钮（参考设计稿的双下箭头）
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                onClick = onDismiss,
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "收起键盘",
                        tint = Accent
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("收起键盘", style = MaterialTheme.typography.labelLarge, color = Accent)
                }
            }
        }

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

        KeyRow(keys = listOf("esc", "tab", "home", "end", "pgup", "pgdn", "del")) { pressCombo(it) }
        KeyRow(keys = ('1'..'9').map { it.toString() } + listOf("0", "-", "=")) { pressChar(it.first()) }
        KeyRow(keys = "qwertyuiop".map { it.toString() }) { pressChar(it.first()) }
        KeyRow(keys = "asdfghjkl".map { it.toString() }) { pressChar(it.first()) }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyCap("⇧", active = sticky.contains("shift"), modifier = Modifier.weight(1f)) { toggle("shift") }
            KeyCap("z", modifier = Modifier.weight(1f)) { pressChar('z') }
            KeyCap("x", modifier = Modifier.weight(1f)) { pressChar('x') }
            KeyCap("c", modifier = Modifier.weight(1f)) { pressChar('c') }
            KeyCap("v", modifier = Modifier.weight(1f)) { pressChar('v') }
            KeyCap("b", modifier = Modifier.weight(1f)) { pressChar('b') }
            KeyCap("n", modifier = Modifier.weight(1f)) { pressChar('n') }
            KeyCap("m", modifier = Modifier.weight(1f)) { pressChar('m') }
            KeyCap(",", modifier = Modifier.weight(0.7f)) { pressChar(',') }
            KeyCap(".", modifier = Modifier.weight(0.7f)) { pressChar('.') }
            IconKeyCap(Icons.AutoMirrored.Outlined.Backspace, modifier = Modifier.weight(1.4f)) { pressCombo("backspace") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyCap("Ctrl", active = sticky.contains("ctrl"), modifier = Modifier.weight(1f)) { toggle("ctrl") }
            KeyCap("⌥", active = sticky.contains("alt"), modifier = Modifier.weight(1f)) { toggle("alt") }
            KeyCap("⌘", active = sticky.contains("cmd"), modifier = Modifier.weight(1f)) { toggle("cmd") }
            KeyCap("space", modifier = Modifier.weight(2f)) { pressChar(' ') }
            IconKeyCap(Icons.AutoMirrored.Outlined.KeyboardReturn, modifier = Modifier.weight(1.4f)) { pressCombo("enter") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.weight(1f))
            KeyCap("←", modifier = Modifier.weight(1f)) { pressCombo("left") }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyCap("↑", modifier = Modifier.fillMaxWidth()) { pressCombo("up") }
                KeyCap("↓", modifier = Modifier.fillMaxWidth()) { pressCombo("down") }
            }
            KeyCap("→", modifier = Modifier.weight(1f)) { pressCombo("right") }
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun KeyRow(keys: List<String>, modifier: Modifier = Modifier, onPress: (String) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { k ->
            KeyCap(k, modifier = Modifier.weight(1f)) { onPress(k) }
        }
    }
}

@Composable
private fun IconKeyCap(icon: ImageVector, modifier: Modifier = Modifier, onPress: () -> Unit) {
    Surface(
        onClick = onPress,
        modifier = modifier.height(44.dp),
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
private fun KeyCap(
    label: String,
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onPress: () -> Unit
) {
    Surface(
        onClick = onPress,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (active) Accent else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
