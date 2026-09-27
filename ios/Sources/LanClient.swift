// TCP 客户端：Network.framework NWConnection，换行分隔 JSON，与桌面接收端协议一致。
// 安全：连接时发送 6 位配对码，校验通过前不发送任何输入事件。
import Foundation
import Network

final class LanClient: ObservableObject {
    enum State: String { case idle = "未连接", connecting = "连接中…", pairing = "配对中…", ready = "已连接", failed = "失败" }

    @Published var state: State = .idle
    @Published var message = ""

    private var connection: NWConnection?
    private var buffer = Data()
    private let queue = DispatchQueue(label: "cmouse.client")

    var isReady: Bool { state == .ready }

    // MARK: - 连接

    func connect(host: String, port: UInt16, code: String, deviceName: String) {
        disconnect()
        state = .connecting
        let conn = NWConnection(
            host: NWEndpoint.Host(host),
            port: NWEndpoint.Port(rawValue: port) ?? 8433,
            using: .tcp)
        connection = conn

        conn.stateUpdateHandler = { [weak self] s in
            DispatchQueue.main.async {
                switch s {
                case .ready:
                    self?.state = .pairing
                    self?.sendHello(code: code, name: deviceName)
                case .failed(let e):
                    self?.state = .failed
                    self?.message = "连接失败：\(e)"
                case .cancelled:
                    if self?.state != .failed { self?.state = .idle }
                default: break
                }
            }
        }
        conn.start(queue: queue)
        receiveLoop(conn)
    }

    private func sendHello(code: String, name: String) {
        send(["t": "hello", "name": name, "code": code, "proto": 1])
    }

    func disconnect() {
        connection?.cancel()
        connection = nil
        buffer.removeAll()
        if state != .failed { state = .idle }
    }

    // MARK: - 接收

    private func receiveLoop(_ conn: NWConnection) {
        conn.receive(minimumIncompleteLength: 1, maximumLength: 16 * 1024) { [weak self] data, _, done, error in
            guard let self else { return }
            if let data, !data.isEmpty {
                self.buffer.append(data)
                self.drain()
            }
            if done || error != nil {
                DispatchQueue.main.async {
                    if self.state == .ready { self.state = .idle; self.message = "连接已断开" }
                }
                return
            }
            self.receiveLoop(conn)
        }
    }

    private func drain() {
        while let idx = buffer.firstIndex(of: 0x0A) {
            let line = String(data: buffer.subdata(in: buffer.startIndex..<idx), encoding: .utf8) ?? ""
            buffer.removeSubrange(buffer.startIndex...idx)
            guard let obj = try? JSONSerialization.jsonObject(with: Data(line.utf8)) as? [String: Any],
                  let t = obj["t"] as? String else { continue }
            DispatchQueue.main.async {
                switch t {
                case "pair-ok":
                    self.state = .ready
                    self.message = ""
                case "pair-fail":
                    self.state = .failed
                    self.message = "配对码错误"
                    self.disconnect()
                default: break
                }
            }
        }
    }

    // MARK: - 输入事件

    private func send(_ dict: [String: Any]) {
        guard isReady, let d = try? JSONSerialization.data(withJSONObject: dict) else { return }
        var payload = d
        payload.append(0x0A)
        connection?.send(content: payload, completion: .contentProcessed { _ in })
    }

    func move(dx: Double, dy: Double) { send(["t": "move", "dx": dx, "dy": dy]) }
    func abs(x: Double, y: Double) { send(["t": "abs", "x": x, "y": y]) }
    func click(_ btn: String = "left", double: Bool = false) { send(["t": "click", "btn": btn, "dbl": double]) }
    func button(_ btn: String, down: Bool) { send(["t": "btn", "btn": btn, "down": down]) }
    func scroll(dx: Double, dy: Double) { send(["t": "scroll", "dx": dx, "dy": dy]) }
    func zoom(_ d: Int) { send(["t": "zoom", "d": d]) }
    func key(_ keys: String) { send(["t": "key", "keys": keys]) }
    func text(_ s: String) { send(["t": "text", "text": s]) }
}
