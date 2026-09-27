import Foundation
import Network

/// TCP 服务：监听 8433 端口 + Bonjour 自动发现（_cmouse._tcp）。
/// 协议：换行分隔 JSON（docs/protocol.md）。
/// 安全：先校验 6 位配对码；未配对连接的输入事件全部丢弃；同一时间只允许一个客户端。
final class Server {

    var onPeerChanged: ((String?) -> Void)?

    private let db: DB
    private var listener: NWListener?
    private var peer: Peer?
    private let queue = DispatchQueue(label: "cmouse.server")

    private var statCounter = 0

    init(db: DB) {
        self.db = db
    }

    func start() {
        let params = NWParameters.tcp
        params.allowLocalEndpointReuse = true
        guard let port = NWEndpoint.Port(rawValue: 8433), let l = try? NWListener(using: params, on: port) else {
            NSLog("cMouse: 端口 8433 监听失败")
            return
        }
        l.service = NWListener.Service(name: Host.current().localizedName ?? "Mac", type: "_cmouse._tcp")
        l.newConnectionHandler = { [weak self] conn in
            self?.accept(conn)
        }
        l.stateUpdateHandler = { state in
            if case .failed(let err) = state { NSLog("cMouse listener failed: \(err)") }
        }
        l.start(queue: queue)
        listener = l
    }

    func stop() {
        listener?.cancel()
        peer?.close()
    }

    private func accept(_ conn: NWConnection) {
        // 单客户端策略：新连接替换旧连接
        peer?.close()
        let p = Peer(connection: conn, db: db) { [weak self] name in
            DispatchQueue.main.async { self?.onPeerChanged?(name) }
            if name == nil { self?.statCounter = 0 }
        }
        p.start()
        peer = p
    }
}

/// 单个手机连接：缓冲、按行解析、配对校验、事件分发。
private final class Peer {
    private let conn: NWConnection
    private let db: DB
    private let onPeer: (String?) -> Void
    private var buffer = Data()
    private var paired = false
    private var name: String?
    private var closed = false

    init(connection: NWConnection, db: DB, onPeer: @escaping (String?) -> Void) {
        self.conn = connection
        self.db = db
        self.onPeer = onPeer
    }

    func start() {
        conn.stateUpdateHandler = { [weak self] state in
            if case .ready = state { self?.receiveLoop() }
            if case .failed = state { self?.close() }
            if case .cancelled = state { self?.close() }
        }
        conn.start(queue: .global())
    }

    func close() {
        guard !closed else { return }
        closed = true
        conn.cancel()
        if paired { onPeer(nil) }
    }

    private func receiveLoop() {
        conn.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            if let data, !data.isEmpty {
                self.buffer.append(data)
                self.drainBuffer()
            }
            if isComplete || error != nil {
                self.close()
                return
            }
            if !self.closed { self.receiveLoop() }
        }
    }

    private func drainBuffer() {
        while let idx = buffer.firstIndex(of: 0x0A) {
            let lineData = buffer.subdata(in: buffer.startIndex..<idx)
            buffer.removeSubrange(buffer.startIndex...idx)
            guard let line = String(data: lineData, encoding: .utf8) else { continue }
            handle(line: line)
        }
    }

    private func send(_ dict: [String: Any], then completion: (() -> Void)? = nil) {
        guard let d = try? JSONSerialization.data(withJSONObject: dict), !closed else {
            completion?()
            return
        }
        var payload = d
        payload.append(0x0A)
        conn.send(content: payload, completion: .contentProcessed { _ in completion?() })
    }

    private func handle(line: String) {
        guard let obj = (try? JSONSerialization.jsonObject(with: Data(line.utf8))) as? [String: Any] else { return }
        let type = obj["t"] as? String ?? ""

        if type == "hello" {
            let code = obj["code"] as? String ?? ""
            name = obj["name"] as? String
            if code == db.pairCode {
                paired = true
                send(["t": "pair-ok"])
                db.rememberDevice(id: conn.endpoint.debugDescription, name: name ?? "iPhone")
                onPeer(name ?? "已连接设备")
            } else {
                // 必须等异步 send 完成后再关闭，否则响应会被取消
                send(["t": "pair-fail"]) { [weak self] in self?.close() }
            }
            return
        }

        guard paired else { return }

        switch type {
        case "move":
            Injector.move(dx: CGFloat(obj["dx"] as? Double ?? 0), dy: CGFloat(obj["dy"] as? Double ?? 0))
        case "abs":
            Injector.absolute(x: CGFloat(obj["x"] as? Double ?? 0), y: CGFloat(obj["y"] as? Double ?? 0))
        case "click":
            Injector.click(obj["btn"] as? String ?? "left", double: obj["dbl"] as? Bool ?? false)
        case "btn":
            Injector.button(obj["btn"] as? String ?? "left", down: obj["down"] as? Bool ?? false)
        case "scroll":
            Injector.scroll(dx: Int(obj["dx"] as? Double ?? 0), dy: Int(obj["dy"] as? Double ?? 0))
        case "zoom":
            Injector.zoom(delta: Int(obj["d"] as? Double ?? 0))
        case "key":
            Injector.combo(obj["keys"] as? String ?? "")
        case "text":
            Injector.text(obj["text"] as? String ?? "")
        case "ping":
            send(["t": "pong"])
        default:
            break
        }
    }
}
