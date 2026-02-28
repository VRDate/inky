// ═══════════════════════════════════════════════════════════════
// UnityViewHandler.iOS.cs — iOS platform partial
// Creates a UIView container; UnityFramework renders into
// the app's key window managed by the iOS bridge.
// ═══════════════════════════════════════════════════════════════

#if IOS
using UIKit;

namespace Ink.Maui.Handlers
{
    public partial class UnityViewHandler
    {
        partial void CreatePlatformContainerImpl(ref object result)
        {
            // Container UIView — UnityFramework will render into
            // the shared key window. This view acts as a placeholder
            // and receives touch events when Unity is visible.
            var container = new UIView
            {
                BackgroundColor = UIColor.Black
            };
            var label = new UILabel
            {
                Text = "Unity 3D View",
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
