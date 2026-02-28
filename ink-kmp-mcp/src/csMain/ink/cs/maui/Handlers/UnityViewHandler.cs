// ═══════════════════════════════════════════════════════════════
// UnityViewHandler.cs — MAUI Handler for UnityView
// Routes cross-platform UnityView commands to platform-specific
// native views. Each platform provides a partial class with
// CreatePlatformView() and platform-specific command handling.
// ═══════════════════════════════════════════════════════════════

using System;
using Ink.Maui.Services;
using Ink.Maui.Views;
using Microsoft.Maui;
using Microsoft.Maui.Handlers;

namespace Ink.Maui.Handlers
{
    /// <summary>
    /// Cross-platform MAUI handler for <see cref="UnityView"/>.
    /// Platform partials in Platforms/{Android,iOS,Windows,MacCatalyst}/.
    /// </summary>
    public partial class UnityViewHandler : ViewHandler<UnityView, object>
    {
        private IUnityBridge? _bridge;

        public static IPropertyMapper<UnityView, UnityViewHandler> Mapper =
            new PropertyMapper<UnityView, UnityViewHandler>(ViewHandler.ViewMapper);

        public static CommandMapper<UnityView, UnityViewHandler> CommandMapper =
            new(ViewHandler.ViewCommandMapper)
            {
                [nameof(UnityView.SendMessage)] = MapSendMessage,
                [nameof(UnityView.Show)] = MapShow,
                [nameof(UnityView.Hide)] = MapHide,
            };

        public UnityViewHandler() : base(Mapper, CommandMapper) { }

        public UnityViewHandler(IUnityBridge bridge) : base(Mapper, CommandMapper)
        {
            _bridge = bridge;
        }

        protected override object CreatePlatformView() => CreatePlatformContainer();

        /// <summary>Platform-specific: creates the native container view.</summary>
        partial void CreatePlatformContainerImpl(ref object result);

        private object CreatePlatformContainer()
        {
            object result = new object();
            CreatePlatformContainerImpl(ref result);
            return result;
        }

        protected override void ConnectHandler(object platformView)
        {
            base.ConnectHandler(platformView);

            if (_bridge != null)
            {
                _bridge.OnMessageReceived += OnUnityMessage;
            }
        }

        protected override void DisconnectHandler(object platformView)
        {
            if (_bridge != null)
            {
                _bridge.OnMessageReceived -= OnUnityMessage;
            }

            base.DisconnectHandler(platformView);
        }

        private void OnUnityMessage(string message)
        {
            VirtualView?.RaiseUnityMessage(message);
        }

        private static void MapSendMessage(UnityViewHandler handler, UnityView view, object? args)
        {
            if (args is UnityMessage msg && handler._bridge != null)
            {
                handler._bridge.SendMessage(msg.GameObject, msg.Method, msg.Message);
            }
        }

        private static void MapShow(UnityViewHandler handler, UnityView view, object? args)
        {
            handler._bridge?.ShowUnity();
            view.RaiseUnityReady();
        }

        private static void MapHide(UnityViewHandler handler, UnityView view, object? args)
        {
            handler._bridge?.HideUnity();
        }
    }
}
