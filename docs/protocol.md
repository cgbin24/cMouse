# cMouse 传输协议（模式 B：Wi-Fi 接收端）

传输层：**TCP**，端口 **8433**（IPv4/IPv6 双栈）。
消息编码：**UTF-8 JSON，每条一行，以 `\n` 结尾**。
发现方式：接收端通过 Bonjour/mDNS 发布服务 `_cmouse._tcp`（Windows 端暂以 IP 直连）。

## 安全模型

1. 连接建立后，客户端必须发送 `hello` 携带 6 位配对码。
2. 接收端校验配对码：通过 → 回 `pair-ok`；失败 → 回 `pair-fail` 并断开。
3. 配对通过前，接收端**丢弃一切输入事件**。
4. 同一时间只允许一个客户端，新连接替换旧连接。
5. 配对码持久化在接收端本地 SQLite，可在菜单/托盘查看。

## 消息

### 客户端 → 接收端

| 消息 | 字段 | 说明 |
|------|------|------|
| `hello` | `name` 设备名、`code` 配对码、`proto` 协议版本 | 首条消息，必须 |
| `move` | `dx`、`dy`（浮点，点） | 相对移动（屏幕坐标系，y 向下为正） |
| `abs` | `x`、`y`（0..1） | 绝对定位（屏幕归一化坐标） |
| `click` | `btn` = `left`/`right`/`middle`；`dbl` 布尔 | 单击；`dbl=true` 为双击 |
| `btn` | `btn`；`down` 布尔 | 按下/抬起（拖拽用） |
| `scroll` | `dx`、`dy`（整数，格） | **内容方向语义**：+dy=内容向下滚 |
| `zoom` | `d`（整数，格） | 缩放：+d=放大（Ctrl+滚轮等效） |
| `key` | `keys` = `"ctrl+up"` 等组合键 | `ctrl`/`shift`/`alt`/`cmd`(macOS⌘, Win 键) + 主键 |
| `text` | `text` 字符串 | Unicode 直传（支持中文整句） |
| `ping` | — | 保活，接收端回 `pong` |

主键支持：`a-z` `0-9`、`enter` `esc` `tab` `space` `backspace` `delete` `home` `end`
`pageup` `pagedown`、`up` `down` `left` `right`、`f1`–`f12`。

### 接收端 → 客户端

| 消息 | 说明 |
|------|------|
| `pair-ok` | 配对成功，此后接受输入事件 |
| `pair-fail` | 配对码错误，连接即将关闭；带 `lock` 字段（整数秒）：0=未锁定，>0=服务端防爆破锁定中（连续 5 次错误锁定 300 秒） |
| `pong` | ping 的应答 |

## 示例

```json
{"t":"hello","name":"iPhone","code":"276663","proto":1}
{"t":"pair-ok"}
{"t":"move","dx":12.5,"dy":-3.0}
{"t":"scroll","dx":0,"dy":3}
{"t":"zoom","d":1}
{"t":"key","keys":"cmd+tab"}
{"t":"text","text":"你好，世界"}
```

## 手势语义映射（客户端实现，接收端无需感知）

| 手势 | 事件序列 |
|------|----------|
| 单指滑动 | `move` 流 |
| 单指轻点 | `click left dbl=false` |
| 单指双击 | `click left dbl=true` |
| 双指滑动 | `scroll` 流（可带惯性） |
| 双指轻点 | `click right` |
| 双指捏合 | `zoom d=±1`（每 8% 跨度变化 1 格） |
| 长按拖动 | `btn left down` + `move` 流 + `btn left up` |
| 三指滑动 | `key`（可配置组合键） |
