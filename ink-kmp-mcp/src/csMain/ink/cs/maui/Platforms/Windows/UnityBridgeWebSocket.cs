// ═══════════════════════════════════════════════════════════════
// UnityBridgeWebSocket.cs — Desktop fallback (Windows/macOS)
// No native UAAL support — communicates with standalone Unity
// build via WebSocket JSON messages.
//
// Unity side runs a WebSocket server; this client connects and
// sends JSON envelopes matching the UnitySendMessage contract:
// { "gameObject": "...", "method": "...", "message": "..." }
// ═══════════════════════════════════════════════════════════════

using System;
using System.Diagnostics;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using System.Threading;
using System.Threading.Tasks;
using Ink.Maui.Services;

namespace Ink.Maui.Platforms.Desktop
{
    /// <summary>
    /// Desktop UAAL fallback — connects to a standalone Unity build
    /// via WebSocket. Used on Windows (WinUI3) and macOS (Catalyst)
    /// where native UAAL embedding is not supported.
    /// </summary>
    public class UnityBridgeWebSocket : IUnityBridge
    {
        private ClientWebSocket? _ws;
        private CancellationTokenSource? _cts;
        private Process? _unityProcess;
        private readonly string _unityExePath;
        private readonly string _wsUrl;

        public UnityBridgeWebSocket(
            string unityExePath = "Unity/InkUnity.exe",
            string wsUrl = "ws://localhost:9090/ink-unity")
        {
            _unityExePath = unityExePath;
            _wsUrl = wsUrl;
        }

        public bool IsLoaded => _ws?.State == WebSocketState.Open;

        public event Action<string>? OnMessageReceived;

        public void ShowUnity()
        {
            // Launch standalone Unity build if not running
            if (_unityProcess == null || _unityProcess.HasExited)
            {
                try
                {
                    _unityProcess = Process.Start(new ProcessStartInfo
                    {
                        FileName = _unityExePath,
                        Arguments = "--websocket-server 9090",
                        UseShellExecute = true
                    });
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine(
                        $"[UnityBridge.WebSocket] Failed to launch Unity: {ex.Message}");
                    return;
                }
            }

            // Connect WebSocket (async fire-and-forget)
            _ = ConnectAsync();
        }

        public void HideUnity()
        {
            // Minimize the Unity window
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

            // Retry connection (Unity takes time to start)
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
                "[UnityBridge.WebSocket] Failed to connect after retries");
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
