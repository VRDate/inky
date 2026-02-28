// ═══════════════════════════════════════════════════════════════
// App.cs — MAUI Application shell
// ═══════════════════════════════════════════════════════════════

using Microsoft.Maui;
using Microsoft.Maui.Controls;

namespace Ink.Maui
{
    public class App : Application
    {
        public App()
        {
            MainPage = new NavigationPage(new StoryPage());
        }
    }
}
