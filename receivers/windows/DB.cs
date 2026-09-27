// 本地数据：SQLite（Microsoft.Data.Sqlite），存配置、配对设备，不上传任何数据。
// 路径：%APPDATA%\cMouse\cmouse.db

using Microsoft.Data.Sqlite;

internal sealed class DB
{
    private readonly SqliteConnection _conn;
    private string? _pairCode;

    public DB()
    {
        var dir = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "cMouse");
        Directory.CreateDirectory(dir);
        var path = Path.Combine(dir, "cmouse.db");
        _conn = new SqliteConnection($"Data Source={path}");
        _conn.Open();
        Exec("""
            CREATE TABLE IF NOT EXISTS kv(key TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS devices(
                id TEXT PRIMARY KEY, name TEXT NOT NULL, last_seen INTEGER NOT NULL);
            """);
    }

    public string PairCode
    {
        get
        {
            if (_pairCode != null) return _pairCode;
            using var cmd = _conn.CreateCommand();
            cmd.CommandText = "SELECT value FROM kv WHERE key='pair_code'";
            var v = cmd.ExecuteScalar() as string;
            if (v is { Length: 6 }) { _pairCode = v; return v; }
            var code = Random.Shared.Next(0, 1_000_000).ToString("D6");
            using var set = _conn.CreateCommand();
            set.CommandText = "INSERT INTO kv(key,value) VALUES('pair_code',$c) " +
                              "ON CONFLICT(key) DO UPDATE SET value=$c";
            set.Parameters.AddWithValue("$c", code);
            set.ExecuteNonQuery();
            _pairCode = code;
            return code;
        }
    }

    public void RememberDevice(string id, string name)
    {
        using var cmd = _conn.CreateCommand();
        cmd.CommandText = """
            INSERT INTO devices(id,name,last_seen) VALUES($id,$name,strftime('%s','now'))
            ON CONFLICT(id) DO UPDATE SET name=$name, last_seen=strftime('%s','now')
            """;
        cmd.Parameters.AddWithValue("$id", id);
        cmd.Parameters.AddWithValue("$name", name);
        cmd.ExecuteNonQuery();
    }

    private void Exec(string sql)
    {
        using var cmd = _conn.CreateCommand();
        cmd.CommandText = sql;
        cmd.ExecuteNonQuery();
    }
}
