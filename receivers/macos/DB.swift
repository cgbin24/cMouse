import Foundation
import SQLite3

/// 本地数据：SQLite（macOS 系统自带 libsqlite3）。
/// 路径：~/Library/Application Support/cMouse/cmouse.db
/// 仅存配置、配对设备与事件统计，不上传任何数据。
final class DB {
    private var handle: OpaquePointer?

    init() {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("cMouse", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let path = dir.appendingPathComponent("cmouse.db").path
        guard sqlite3_open(path, &handle) == SQLITE_OK else {
            handle = nil
            return
        }
        exec("PRAGMA journal_mode=WAL;")
        exec("""
        CREATE TABLE IF NOT EXISTS kv(key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE IF NOT EXISTS devices(
            id TEXT PRIMARY KEY, name TEXT NOT NULL, last_seen INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS stats(day TEXT PRIMARY KEY, events INTEGER NOT NULL);
        """)
    }

    deinit {
        if let h = handle { sqlite3_close(h) }
    }

    @discardableResult
    private func exec(_ sql: String) -> Bool {
        sqlite3_exec(handle, sql, nil, nil, nil) == SQLITE_OK
    }

    func get(_ key: String) -> String? {
        var stmt: OpaquePointer?
        defer { sqlite3_finalize(stmt) }
        guard sqlite3_prepare_v2(handle, "SELECT value FROM kv WHERE key=?1", -1, &stmt, nil) == SQLITE_OK else { return nil }
        sqlite3_bind_text(stmt, 1, key, -1, SQLITE_TRANSIENT)
        guard sqlite3_step(stmt) == SQLITE_ROW else { return nil }
        return String(cString: sqlite3_column_text(stmt, 0))
    }

    func set(_ key: String, _ value: String) {
        var stmt: OpaquePointer?
        defer { sqlite3_finalize(stmt) }
        guard sqlite3_prepare_v2(handle, "INSERT INTO kv(key,value) VALUES(?1,?2) ON CONFLICT(key) DO UPDATE SET value=?2", -1, &stmt, nil) == SQLITE_OK else { return }
        sqlite3_bind_text(stmt, 1, key, -1, SQLITE_TRANSIENT)
        sqlite3_bind_text(stmt, 2, value, -1, SQLITE_TRANSIENT)
        _ = sqlite3_step(stmt)
    }

    func rememberDevice(id: String, name: String) {
        var stmt: OpaquePointer?
        defer { sqlite3_finalize(stmt) }
        guard sqlite3_prepare_v2(handle, "INSERT INTO devices(id,name,last_seen) VALUES(?1,?2,?3) ON CONFLICT(id) DO UPDATE SET name=?2, last_seen=?3", -1, &stmt, nil) == SQLITE_OK else { return }
        sqlite3_bind_text(stmt, 1, id, -1, SQLITE_TRANSIENT)
        sqlite3_bind_text(stmt, 2, name, -1, SQLITE_TRANSIENT)
        sqlite3_bind_int64(stmt, 3, Int64(Date().timeIntervalSince1970))
        _ = sqlite3_step(stmt)
    }

    func bumpStats() {
        let day = DateFormatter.yyyyMMdd.string(from: Date())
        exec("INSERT INTO stats(day,events) VALUES('\(day)',1) ON CONFLICT(day) DO UPDATE SET events=events+1;")
    }

    var pairCode: String {
        if let c = get("pair_code"), c.count == 6 { return c }
        let code = String(format: "%06d", Int.random(in: 0...999_999))
        set("pair_code", code)
        return code
    }

    /// 一键清除全部本地数据（配对码、已配对设备、统计）。
    /// 清除后配对码将重新生成，所有手机需要重新配对。
    func clearAll() {
        exec("DELETE FROM kv;")
        exec("DELETE FROM devices;")
        exec("DELETE FROM stats;")
    }
}

private extension DateFormatter {
    static let yyyyMMdd: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()
}

private let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
