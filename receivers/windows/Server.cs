// TCP 服务：监听 8433 端口，换行分隔 JSON 协议（docs/protocol.md）。
// 安全：先校验 6 位配对码；未配对连接的输入事件全部丢弃；同一时间只允许一个客户端。

using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;

internal sealed class Server
{
    public event Action<string?>? PeerChanged;

    private readonly DB _db;
    private TcpListener? _listener;
    private TcpClient? _current;
    private volatile bool _running;

    public string? ConnectedName { get; private set; }

    // 配对码防爆破：连续 5 次错误锁定 300 秒
    private const int MaxPairAttempts = 5;
    private const int PairLockSeconds = 300;
    private int _failedAttempts;
    private DateTime? _lockUntil;

    /// 返回剩余锁定秒数；0 表示未锁定。
    private int PairLockRemainingSeconds()
    {
        if (_lockUntil.HasValue)
        {
            if (DateTime.UtcNow < _lockUntil.Value)
                return (int)Math.Ceiling((_lockUntil.Value - DateTime.UtcNow).TotalSeconds);
            _lockUntil = null;
            _failedAttempts = 0;
        }
        return 0;
    }

    private int RecordPairFailure()
    {
        _failedAttempts++;
        if (_failedAttempts >= MaxPairAttempts)
        {
            _lockUntil = DateTime.UtcNow.AddSeconds(PairLockSeconds);
            _failedAttempts = 0;
            return PairLockSeconds;
        }
        return 0;
    }

    /// 断开当前客户端（清除数据后调用，强制重新配对）。
    public void DisconnectCurrent()
    {
        try { _current?.Close(); } catch { }
    }

    public Server(DB db) { _db = db; }

    public void Start()
    {
        _running = true;
        _listener = new TcpListener(IPAddress.IPv6Any, 8433);
        _listener.Server.DualMode = true; // 同时接受 IPv4/IPv6
        try
        {
            _listener.Start();
        }
        catch (Exception e)
        {
            Console.Error.WriteLine($"端口 8433 监听失败：{e.Message}");
            return;
        }
        Task.Run(AcceptLoop);
    }

    public void Stop()
    {
        _running = false;
        try { _current?.Close(); } catch { }
        try { _listener?.Stop(); } catch { }
    }

    private async Task AcceptLoop()
    {
        while (_running)
        {
            try
            {
                var client = await _listener!.AcceptTcpClientAsync();
                // 单客户端策略：新连接替换旧连接
                ConnectedName = null;
                PeerChanged?.Invoke(null);
                try { _current?.Close(); } catch { }
                _current = client;
                _ = Task.Run(() => Serve(client));
            }
            catch (Exception) when (!_running) { break; }
            catch (Exception) { /* 继续接受 */ }
        }
    }

    private async Task Serve(TcpClient client)
    {
        using var _ = client;
        client.NoDelay = true;
        var stream = client.GetStream();
        var remote = (client.Client.RemoteEndPoint as IPEndPoint)?.Address?.ToString() ?? "unknown";
        bool paired = false;
        string name = "";

        var buf = new byte[64 * 1024];
        var sb = new StringBuilder();

        while (_running && client.Connected)
        {
            int n;
            try { n = await stream.ReadAsync(buf, 0, buf.Length); }
            catch { break; }
            if (n == 0) break;
            sb.Append(Encoding.UTF8.GetString(buf, 0, n));

            int idx;
            while ((idx = sb.ToString().IndexOf('\n')) >= 0)
            {
                var line = sb.ToString(0, idx);
                sb.Remove(0, idx + 1);
                if (line.Length == 0) continue;

                JsonDocument? doc = null;
                try { doc = JsonDocument.Parse(line); }
                catch { continue; }
                using (doc)
                {
                    var type = doc.RootElement.TryGetProperty("t", out var t) ? t.GetString() : null;

                    if (type == "hello")
                    {
                        // 防爆破：锁定期间直接拒绝
                        var locked = PairLockRemainingSeconds();
                        if (locked > 0)
                        {
                            await SendAsync(stream, "{\"t\":\"pair-fail\",\"lock\":" + locked + "}");
                            return;
                        }
                        var code = doc.RootElement.TryGetProperty("code", out var c) ? c.GetString() : null;
                        name = doc.RootElement.TryGetProperty("name", out var nm) ? nm.GetString() ?? "手机" : "手机";
                        if (code == _db.PairCode)
                        {
                            paired = true;
                            _failedAttempts = 0;
                            _lockUntil = null;
                            ConnectedName = name;
                            _db.RememberDevice(remote, name);
                            await SendAsync(stream, """{"t":"pair-ok"}""");
                            PeerChanged?.Invoke(name);
                        }
                        else
                        {
                            var lockSec = RecordPairFailure();
                            await SendAsync(stream, "{\"t\":\"pair-fail\",\"lock\":" + lockSec + "}");
                            return; // 关闭连接
                        }
                        continue;
                    }

                    if (!paired) continue;

                    switch (type)
                    {
                        case "move":
                            Injector.Move((int)Num(doc, "dx"), (int)Num(doc, "dy"));
                            break;
                        case "abs":
                            Injector.Absolute(Num(doc, "x"), Num(doc, "y"));
                            break;
                        case "click":
                            Injector.Click(Str(doc, "btn", "left"), Bool(doc, "dbl"));
                            break;
                        case "btn":
                            Injector.Button(Str(doc, "btn", "left"), Bool(doc, "down"));
                            break;
                        case "scroll":
                            Injector.Scroll((int)Num(doc, "dx"), (int)Num(doc, "dy"));
                            break;
                        case "zoom":
                            Injector.Zoom((int)Num(doc, "d"));
                            break;
                        case "key":
                            Injector.Combo(Str(doc, "keys", ""));
                            break;
                        case "text":
                            Injector.Text(Str(doc, "text", ""));
                            break;
                        case "ping":
                            await SendAsync(stream, """{"t":"pong"}""");
                            break;
                    }
                }
            }
        }
        if (paired) { ConnectedName = null; PeerChanged?.Invoke(null); }
    }

    private static async Task SendAsync(NetworkStream s, string json)
    {
        var b = Encoding.UTF8.GetBytes(json + "\n");
        await s.WriteAsync(b);
        await s.FlushAsync();
    }

    private static double Num(JsonDocument? d, string key) =>
        d != null && d.RootElement.TryGetProperty(key, out var v) &&
        (v.ValueKind == JsonValueKind.Number) ? v.GetDouble() : 0;

    private static string Str(JsonDocument? d, string key, string def) =>
        d != null && d.RootElement.TryGetProperty(key, out var v) && v.ValueKind == JsonValueKind.String
            ? v.GetString() ?? def : def;

    private static bool Bool(JsonDocument? d, string key) =>
        d != null && d.RootElement.TryGetProperty(key, out var v) && v.ValueKind == JsonValueKind.True;
}

internal static class Net
{
    public static List<string> LocalIPv4()
    {
        var result = new List<string>();
        try
        {
            foreach (var ip in Dns.GetHostEntry(Dns.GetHostName()).AddressList)
                if (ip.AddressFamily == AddressFamily.InterNetwork)
                    result.Add(ip.ToString());
        }
        catch { }
        return result;
    }
}
