// ═══════════════════════════════════════════════════════════════
// StoryPage.cs — Main ink story interaction page
// Text display, choice buttons, Unity 3D view, save/load.
// Connects to IUnityBridge via UnityEventBridge for asset events.
// ═══════════════════════════════════════════════════════════════

using System;
using Ink.Common;
using Ink.Maui.Services;
using Ink.Maui.Views;
using Microsoft.Maui.Controls;

namespace Ink.Maui
{
    public class StoryPage : ContentPage
    {
        private readonly IInkRuntime _runtime;
        private readonly UnityEventBridge? _unityBridge;
        private string? _sessionId;

        private readonly Label _storyText;
        private readonly StackLayout _choicesLayout;
        private readonly Button _continueButton;
        private readonly UnityView _unityView;
        private readonly Button _toggleUnityButton;

        public StoryPage(IInkRuntime runtime, UnityEventBridge? unityBridge = null)
        {
            _runtime = runtime;
            _unityBridge = unityBridge;

            Title = "Inky Story";

            _storyText = new Label
            {
                FontSize = 16,
                Padding = new Thickness(16),
                LineBreakMode = LineBreakMode.WordWrap
            };

            _choicesLayout = new StackLayout
            {
                Padding = new Thickness(16, 0)
            };

            _continueButton = new Button
            {
                Text = "Continue",
                Margin = new Thickness(16),
                IsVisible = false
            };
            _continueButton.Clicked += (_, _) => OnContinue();

            // ── Unity 3D view ──
            _unityView = new UnityView
            {
                HeightRequest = 300,
                IsVisible = false
            };
            _unityView.OnUnityReady += () =>
            {
                System.Diagnostics.Debug.WriteLine("[StoryPage] Unity ready");
            };
            _unityView.OnUnityMessage += msg =>
            {
                System.Diagnostics.Debug.WriteLine($"[StoryPage] Unity message: {msg}");
            };

            _toggleUnityButton = new Button
            {
                Text = "Show 3D View",
                Margin = new Thickness(16, 8)
            };
            _toggleUnityButton.Clicked += (_, _) => ToggleUnityView();

            // ── Toolbar ──
            var toolbar = new StackLayout
            {
                Orientation = StackOrientation.Horizontal,
                Padding = new Thickness(16, 4),
                Children =
                {
                    CreateToolbarButton("Save", OnSave),
                    CreateToolbarButton("Load", OnLoad),
                    CreateToolbarButton("Reset", OnReset),
                    _toggleUnityButton
                }
            };

            Content = new ScrollView
            {
                Content = new StackLayout
                {
                    Children =
                    {
                        toolbar,
                        _unityView,
                        _storyText,
                        _choicesLayout,
                        _continueButton
                    }
                }
            };
        }

        public void LoadStoryJson(string json)
        {
            _sessionId = _runtime.StartStory(json);
            OnContinue();
        }

        private void OnContinue()
        {
            if (_sessionId == null) return;

            var state = _runtime.ContinueStory(_sessionId);
            UpdateDisplay(state);
        }

        private void OnChoose(int index)
        {
            if (_sessionId == null) return;

            var state = _runtime.Choose(_sessionId, index);
            UpdateDisplay(state);
        }

        private void UpdateDisplay(StoryStateDto state)
        {
            _storyText.Text = state.Text;
            _continueButton.IsVisible = state.CanContinue;

            _choicesLayout.Children.Clear();
            foreach (var choice in state.Choices)
            {
                var btn = new Button { Text = choice.Text };
                var idx = choice.Index;
                btn.Clicked += (_, _) => OnChoose(idx);
                _choicesLayout.Children.Add(btn);
            }

            // Forward asset events to Unity if bridge is active
            if (_sessionId != null && _unityView.IsVisible)
            {
                var events = _runtime.GetAssetEvents(_sessionId);
                foreach (var evt in events)
                {
                    _unityView.SendMessage(
                        UnityInkTargets.AssetEventReceiver,
                        UnityInkTargets.ProcessEvent,
                        System.Text.Json.JsonSerializer.Serialize(evt));
                }
            }
        }

        private void ToggleUnityView()
        {
            _unityView.IsVisible = !_unityView.IsVisible;
            if (_unityView.IsVisible)
            {
                _unityView.Show();
                _toggleUnityButton.Text = "Hide 3D View";
            }
            else
            {
                _unityView.Hide();
                _toggleUnityButton.Text = "Show 3D View";
            }
        }

        private string? _savedState;

        private void OnSave(object? sender, EventArgs e)
        {
            if (_sessionId == null) return;
            _savedState = _runtime.SaveState(_sessionId);
        }

        private void OnLoad(object? sender, EventArgs e)
        {
            if (_sessionId == null || _savedState == null) return;
            _runtime.LoadState(_sessionId, _savedState);
            OnContinue();
        }

        private void OnReset(object? sender, EventArgs e)
        {
            if (_sessionId == null) return;
            _runtime.ResetStory(_sessionId);
            OnContinue();
        }

        private static Button CreateToolbarButton(string text, EventHandler handler)
        {
            var btn = new Button
            {
                Text = text,
                FontSize = 12,
                Padding = new Thickness(8, 4),
                Margin = new Thickness(2, 0)
            };
            btn.Clicked += handler;
            return btn;
        }
    }
}
