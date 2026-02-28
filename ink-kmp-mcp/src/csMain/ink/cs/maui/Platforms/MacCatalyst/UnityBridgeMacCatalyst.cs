// ═══════════════════════════════════════════════════════════════
// UnityBridgeMacCatalyst.cs — macOS Catalyst fallback (same as Windows)
// No native UAAL support — communicates with standalone Unity
// build via WebSocket JSON messages.
// ═══════════════════════════════════════════════════════════════

#if MACCATALYST
using System;
using System.Diagnostics;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Ink.Maui.Services;

namespace Ink.Maui.Platforms.MacCatalyst
{
    /// <summary>
    /// macOS Catalyst UAAL fallback — connects to a standalone Unity build
    /// via WebSocket. Same approach as Windows (no native UAAL embedding).
    /// </summary>
    public class UnityBridgeMacCatalyst : IUnityBridge
    {
        private ClientWebSocket? _ws;
        private CancellationTokenSource? _cts;
        private Process? _unityProcess;
        private readonly string _unityAppPath;
        private readonly string _wsUrl;

        public UnityBridgeMacCatalyst(
            string unityAppPath = "InkUnity.app",
            string wsUrl = "ws://localhost:9090/ink-unity")
        {
            _unityAppPath = unityAppPath;
            _wsUrl = wsUrl;
        }

        public bool IsLoaded => _ws?.State == WebSocketState.Open;

        public event Action<string>? OnMessageReceived;

        public void ShowUnity()
        {
            if (_unityProcess == null || _unityProcess.HasExited)
            {
                try
                {
                    _unityProcess = Process.Start(new ProcessStartInfo
                    {
                        FileName = "open",
                        Arguments = $"-a \"{_unityAppPath}\" --args --websocket-server 9090",
                        UseShellExecute = true
                    });
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine(
                        $"[UnityBridge.MacCatalyst] Failed to launch Unity: {ex.Message}");
                    return;
                }
            }

            _ = ConnectAsync();
        }

        public void HideUnity()
        {
            SendMessage("InkUnityUaal", "Minimize", "");
        }

        public void UnloadUnity()
        {
            SendMessage("InkUnityUaal", "Quit", "");
            _cts?.Cancel();
            _ws?.Dispose();
            _ws = null;

            if (_unityProcess is { HasExited: false })
            {
                _unityProcess.CloseMainWindow();
            }
            _unityProcess = null;
        }

        public void SendMessage(string gameObject, string method, string message)
        {
            if (_ws?.State != WebSocketState.Open) return;

            var envelope = JsonSerializer.Serialize(new
            {
                gameObject,
                method,
                message
            });

            var bytes = Encoding.UTF8.GetBytes(envelope);
            _ = _ws.SendAsync(
                new ArraySegment<byte>(bytes),
                WebSocketMessageType.Text,
                true,
                _cts?.Token ?? CancellationToken.None);
        }

        private async Task ConnectAsync()
        {
            _ws = new ClientWebSocket();
            _cts = new CancellationTokenSource();

            for (var i = 0; i < 10; i++)
            {
                try
                {
                    await _ws.ConnectAsync(new Uri(_wsUrl), _cts.Token);
                    _ = ReceiveLoop(_cts.Token);
                    return;
                }
                catch
                {
                    await Task.Delay(1000, _cts.Token);
                }
            }

            System.Diagnostics.Debug.WriteLine(
                "[UnityBridge.MacCatalyst] Failed to connect after retries");
        }

        private async Task ReceiveLoop(CancellationToken ct)
        {
            var buffer = new byte[4096];
            while (!ct.IsCancellationRequested && _ws?.State == WebSocketState.Open)
            {
                try
                {
                    var result = await _ws.ReceiveAsync(
                        new ArraySegment<byte>(buffer), ct);
                    if (result.MessageType == WebSocketMessageType.Close) break;

                    var json = Encoding.UTF8.GetString(buffer, 0, result.Count);
                    OnMessageReceived?.Invoke(json);
                }
                catch (OperationCanceledException) { break; }
                catch { /* log and continue */ }
            }
        }
    }
}
#endif
