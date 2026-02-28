// ═══════════════════════════════════════════════════════════════
// UnityViewHandler.Windows.cs — Windows (WinUI3) platform partial
// Shows a placeholder panel; Unity runs as a standalone process
// communicating via WebSocket.
// ═══════════════════════════════════════════════════════════════

#if WINDOWS
using Microsoft.UI.Xaml.Controls;

namespace Ink.Maui.Handlers
{
    public partial class UnityViewHandler
    {
        partial void CreatePlatformContainerImpl(ref object result)
        {
            // Placeholder panel — Unity runs in a separate window,
            // connected via WebSocket (UnityBridgeWebSocket).
            var panel = new StackPanel();
            var text = new TextBlock
            {
                Text = "Unity 3D (standalone — WebSocket connected)",
                HorizontalAlignment = Microsoft.UI.Xaml.HorizontalAlignment.Center,
                VerticalAlignment = Microsoft.UI.Xaml.VerticalAlignment.Center
            };
            panel.Children.Add(text);
            result = panel;
        }
    }
}
#endif
