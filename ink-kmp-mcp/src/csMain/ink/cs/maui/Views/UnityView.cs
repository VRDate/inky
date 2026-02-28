// ═══════════════════════════════════════════════════════════════
// UnityView.cs — MAUI View that hosts the Unity rendering surface
// Uses the Handler pattern: each platform provides its own
// handler (Android FrameLayout, iOS UIView, desktop placeholder).
// ═══════════════════════════════════════════════════════════════

using System;
using Microsoft.Maui.Controls;

namespace Ink.Maui.Views
{
    /// <summary>
    /// Cross-platform MAUI control that hosts the Unity UAAL surface.
    /// Platform handlers create the native view (Android/iOS) or
    /// show a placeholder (Windows/macOS with WebSocket fallback).
    /// </summary>
    public class UnityView : View, IUnityView
    {
        /// <summary>Fired when Unity rendering is ready.</summary>
        public event Action? OnUnityReady;

        /// <summary>Fired when Unity sends a message to the host.</summary>
        public event Action<string>? OnUnityMessage;

        /// <summary>Send a message to a Unity GameObject.</summary>
        public void SendMessage(string gameObject, string method, string message)
        {
            Handler?.Invoke(nameof(SendMessage),
                new UnityMessage(gameObject, method, message));
        }

        /// <summary>Show the Unity rendering surface.</summary>
        public void Show()
        {
            Handler?.Invoke(nameof(Show), null);
        }

        /// <summary>Hide the Unity rendering surface.</summary>
        public void Hide()
        {
            Handler?.Invoke(nameof(Hide), null);
        }

        internal void RaiseUnityReady() => OnUnityReady?.Invoke();
        internal void RaiseUnityMessage(string msg) => OnUnityMessage?.Invoke(msg);
    }

    /// <summary>Interface for platform-specific UnityView handlers.</summary>
    public interface IUnityView : Microsoft.Maui.IView
    {
        void SendMessage(string gameObject, string method, string message);
        void Show();
        void Hide();
    }

    /// <summary>Message payload for UnitySendMessage calls.</summary>
    public record UnityMessage(string GameObject, string Method, string Message);
}
