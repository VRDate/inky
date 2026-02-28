// ═══════════════════════════════════════════════════════════════
// UnityViewHandler.Android.cs — Android platform partial
// Creates a FrameLayout placeholder; actual Unity rendering
// happens in the separate UnityPlayerActivity (AAR).
// ═══════════════════════════════════════════════════════════════

#if ANDROID
using Android.Widget;

namespace Ink.Maui.Handlers
{
    public partial class UnityViewHandler
    {
        partial void CreatePlatformContainerImpl(ref object result)
        {
            // Placeholder FrameLayout — Unity renders in its own Activity
            // via the AAR's UnityPlayerActivity. This view shows status
            // or a launch button when Unity is not in the foreground.
            var layout = new FrameLayout(Context!);
            var label = new TextView(Context!)
            {
                Text = "Unity 3D View (tap to launch)"
            };
            layout.AddView(label);
            result = layout;
        }
    }
}
#endif
