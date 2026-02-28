// ═══════════════════════════════════════════════════════════════
// UnityBridgeAndroid.cs — Android UAAL bridge
// Hosts Unity via unityLibrary-release.aar (exported from
// ink-unity/ UAAL project). Uses UnitySendMessage for
// MAUI → Unity communication.
//
// Unity GameObjects: InkAssetEventReceiver, InkOneJsBinding
// (from ink-unity/InkAssetEventReceiver.cs, InkOneJsBinding.cs)
// ═══════════════════════════════════════════════════════════════

#if ANDROID
using System;
using Android.App;
using Android.Content;
using Ink.Maui.Services;

namespace Ink.Maui.Platforms.Android
{
    /// <summary>
    /// Android UAAL bridge — launches UnityPlayerActivity from the AAR
    /// and uses <c>UnitySendMessage</c> to forward asset events to
    /// <c>InkAssetEventReceiver.ProcessEvent(json)</c>.
    /// </summary>
    public class UnityBridgeAndroid : IUnityBridge
    {
        private bool _isLoaded;

        public bool IsLoaded => _isLoaded;

        public event Action<string>? OnMessageReceived;

        public void ShowUnity()
        {
            var context = Microsoft.Maui.ApplicationModel.Platform.CurrentActivity;
            if (context == null) return;

            // Launch Unity's player activity from the AAR
            var unityActivityType = Java.Lang.Class.ForName(
                "com.unity3d.player.UnityPlayerActivity");
            var intent = new Intent(context, unityActivityType);
            intent.SetFlags(ActivityFlags.ReorderToFront);
            context.StartActivity(intent);
            _isLoaded = true;
        }

        public void HideUnity()
        {
            // Bring MAUI activity back to front
            var context = Microsoft.Maui.ApplicationModel.Platform.CurrentActivity;
            if (context == null) return;

            var intent = new Intent(context, context.GetType());
            intent.SetFlags(ActivityFlags.ReorderToFront);
            context.StartActivity(intent);
        }

        public void UnloadUnity()
        {
            // Send quit message to Unity
            SendMessage("InkUnityUaal", "Quit", "");
            _isLoaded = false;
        }

        public void SendMessage(string gameObject, string method, string message)
        {
            if (!_isLoaded) return;

            // Static call into Unity player (from AAR)
            try
            {
                var playerClass = Java.Lang.Class.ForName("com.unity3d.player.UnityPlayer");
                var sendMethod = playerClass.GetMethod("UnitySendMessage",
                    Java.Lang.Class.FromType(typeof(Java.Lang.String)),
                    Java.Lang.Class.FromType(typeof(Java.Lang.String)),
                    Java.Lang.Class.FromType(typeof(Java.Lang.String)));
                sendMethod.Invoke(null,
                    new Java.Lang.String(gameObject),
                    new Java.Lang.String(method),
                    new Java.Lang.String(message));
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine(
                    $"[UnityBridge.Android] SendMessage failed: {ex.Message}");
            }
        }

        /// <summary>
        /// Called by Unity native plugin to send messages back to MAUI.
        /// Register this callback via Android broadcast or JNI interface.
        /// </summary>
        public void ReceiveFromUnity(string message)
        {
            OnMessageReceived?.Invoke(message);
        }
    }
}
#endif
