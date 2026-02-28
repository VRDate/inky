// ═══════════════════════════════════════════════════════════════
// UnityViewHandler.MacCatalyst.cs — macOS Catalyst platform partial
// Shows a placeholder NSView; Unity runs as a standalone .app
// communicating via WebSocket (same as Windows fallback).
// ═══════════════════════════════════════════════════════════════

#if MACCATALYST
using UIKit;

namespace Ink.Maui.Handlers
{
    public partial class UnityViewHandler
    {
        partial void CreatePlatformContainerImpl(ref object result)
        {
            // Placeholder UIView — Unity runs as a separate .app,
            // connected via WebSocket (UnityBridgeMacCatalyst).
            var container = new UIView
            {
                BackgroundColor = UIColor.DarkGray
            };
            var label = new UILabel
            {
                Text = "Unity 3D (standalone — WebSocket connected)",
                TextColor = UIColor.White,
                TextAlignment = UITextAlignment.Center,
                TranslatesAutoresizingMaskIntoConstraints = false
            };
            container.AddSubview(label);
            NSLayoutConstraint.ActivateConstraints(new[]
            {
                label.CenterXAnchor.ConstraintEqualTo(container.CenterXAnchor),
                label.CenterYAnchor.ConstraintEqualTo(container.CenterYAnchor)
            });
            result = container;
        }
    }
}
#endif
