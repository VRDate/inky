// ═══════════════════════════════════════════════════════════════
// UnityBridgeIos.cs — iOS UAAL bridge
// Hosts Unity via UnityFramework.framework (exported from
// ink-unity/ UAAL project). Uses SendMessageToGO for
// MAUI → Unity communication.
//
// Unity GameObjects: InkAssetEventReceiver, InkOneJsBinding
// (from ink-unity/InkAssetEventReceiver.cs, InkOneJsBinding.cs)
// ═══════════════════════════════════════════════════════════════

#if IOS
using System;
using System.Runtime.InteropServices;
using Foundation;
using ObjCRuntime;
using Ink.Maui.Services;

namespace Ink.Maui.Platforms.iOS
{
    /// <summary>
    /// iOS UAAL bridge — loads UnityFramework and uses
    /// <c>SendMessageToGO</c> to forward asset events to
    /// <c>InkAssetEventReceiver.ProcessEvent(json)</c>.
    /// </summary>
    public class UnityBridgeIos : IUnityBridge
    {
        private NSObject? _unityFramework;
        private bool _isLoaded;

        public bool IsLoaded => _isLoaded;

        public event Action<string>? OnMessageReceived;

        [DllImport("__Internal")]
        private static extern void UnitySendMessage(string obj, string method, string msg);

        public void ShowUnity()
        {
            if (_unityFramework == null)
            {
                LoadUnityFramework();
            }

            // Show Unity window
            var selector = new Selector("showUnityWindow");
            if (_unityFramework?.RespondsToSelector(selector) == true)
            {
                Messaging.void_objc_msgSend(
                    _unityFramework.Handle, selector.Handle);
            }
            _isLoaded = true;
        }

        public void HideUnity()
        {
            // Pause Unity rendering
            var selector = new Selector("pause:");
            if (_unityFramework?.RespondsToSelector(selector) == true)
            {
                Messaging.void_objc_msgSend_bool(
                    _unityFramework.Handle, selector.Handle, true);
            }
        }

        public void UnloadUnity()
        {
            var selector = new Selector("unloadApplication");
            if (_unityFramework?.RespondsToSelector(selector) == true)
            {
                Messaging.void_objc_msgSend(
                    _unityFramework.Handle, selector.Handle);
            }
            _unityFramework = null;
            _isLoaded = false;
        }

        public void SendMessage(string gameObject, string method, string message)
        {
            if (!_isLoaded) return;

            try
            {
                // Direct P/Invoke into Unity's C function
                UnitySendMessage(gameObject, method, message);
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine(
                    $"[UnityBridge.iOS] SendMessage failed: {ex.Message}");
            }
        }

        private void LoadUnityFramework()
        {
            // Load UnityFramework from the embedded bundle
            var bundlePath = NSBundle.MainBundle.BundlePath +
                "/Frameworks/UnityFramework.framework";
            var bundle = NSBundle.FromPath(bundlePath);
            if (bundle == null)
            {
                System.Diagnostics.Debug.WriteLine(
                    "[UnityBridge.iOS] UnityFramework.framework not found");
                return;
            }

            bundle.Load();

            // Get the UnityFramework class and create instance
            var fwClass = new Class("UnityFramework");
            var getInstance = new Selector("getInstance");
            var handle = Messaging.IntPtr_objc_msgSend(fwClass.Handle, getInstance.Handle);
            _unityFramework = ObjCRuntime.Runtime.GetNSObject(handle);

            if (_unityFramework == null)
            {
                System.Diagnostics.Debug.WriteLine(
                    "[UnityBridge.iOS] Failed to get UnityFramework instance");
                return;
            }

            // Set data bundle ID and run embedded
            var setDataBundle = new Selector("setDataBundleId:");
            Messaging.void_objc_msgSend_IntPtr(
                _unityFramework.Handle, setDataBundle.Handle,
                NSString.CreateNative("com.unity3d.framework"));

            var runEmbedded = new Selector("runEmbeddedWithArgc:argv:appLaunchOpts:");
            Messaging.void_objc_msgSend_int_IntPtr_IntPtr(
                _unityFramework.Handle, runEmbedded.Handle,
                0, IntPtr.Zero, IntPtr.Zero);
        }

        /// <summary>
        /// Called by Unity native plugin to send messages back to MAUI.
        /// </summary>
        public void ReceiveFromUnity(string message)
        {
            OnMessageReceived?.Invoke(message);
        }
    }
}
#endif
