// ═══════════════════════════════════════════════════════════════
// MauiProgram.cs — MAUI app entry point with SteelToe services
// Registers IInkRuntime, IAssetEventTransport, IUnityBridge,
// UnityView handler, and service discovery.
// ═══════════════════════════════════════════════════════════════

using Ink.Common;
using Ink.Common.Transport;
using Ink.Maui.Handlers;
using Ink.Maui.Services;
using Ink.Maui.Views;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Maui.Hosting;

namespace Ink.Maui
{
    public static class MauiProgram
    {
        public static MauiApp CreateMauiApp()
        {
            var builder = MauiApp.CreateBuilder();

            builder.UseMauiApp<App>();

            // ── UnityView handler registration ──
            builder.ConfigureMauiHandlers(handlers =>
            {
                handlers.AddHandler<UnityView, UnityViewHandler>();
            });

            // ── Ink runtime ──
            builder.Services.AddSingleton<IInkRuntime, InkRuntimeService>();

            // ── Transport (WebSocket + msgpack to Kotlin MCP server) ──
            builder.Services.AddSingleton<IAssetEventTransport, MsgpackAssetEventClient>();

            // ── Platform-specific Unity bridge ──
#if ANDROID
            builder.Services.AddSingleton<IUnityBridge, Platforms.Android.UnityBridgeAndroid>();
#elif IOS
            builder.Services.AddSingleton<IUnityBridge, Platforms.iOS.UnityBridgeIos>();
#elif MACCATALYST
            builder.Services.AddSingleton<IUnityBridge, Platforms.MacCatalyst.UnityBridgeMacCatalyst>();
#elif WINDOWS
            builder.Services.AddSingleton<IUnityBridge, Platforms.Desktop.UnityBridgeWebSocket>();
#endif

            // ── Unity event bridge (transport → Unity forwarding) ──
            builder.Services.AddSingleton<UnityEventBridge>();

            // ── SteelToe cloud-native ──
            // builder.AddDiscoveryClient();
            // builder.AddConfigServer();

            return builder.Build();
        }
    }
}
