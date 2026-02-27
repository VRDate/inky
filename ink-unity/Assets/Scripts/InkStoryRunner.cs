// Primary C# ink runtime driver.
// Uses Ink.Runtime.Story from full C# source (Assets/Ink/InkRuntime/).
// All code compiles for WebGL (IL2CPP).

using UnityEngine;
using System.Collections.Generic;
// TODO: Uncomment when ink C# source is added:
// using Ink.Runtime;

namespace InkUnity
{
    public class InkStoryRunner : MonoBehaviour
    {
        // TODO: Replace with actual Ink.Runtime.Story when source is added
        // private Story _story;
        private string _json;
        private bool _started;

        public string StartStory(string json)
        {
            _json = json;
            _started = true;
            // _story = new Story(json);
            Debug.Log("[InkStoryRunner] Story started (C# runtime)");
            return "unity-csharp-1";
        }

        public string ContinueStory()
        {
            if (!_started) return "";
            // while (_story.canContinue) text += _story.Continue();
            return "[C# ink runtime — connect source to enable]";
        }

        public List<string> GetChoices()
        {
            if (!_started) return new List<string>();
            // return _story.currentChoices.Select(c => c.text).ToList();
            return new List<string>();
        }

        public void Choose(int index)
        {
            if (!_started) return;
            // _story.ChooseChoiceIndex(index);
        }

        public bool CanContinue => false; // _story?.canContinue ?? false;

        public List<string> GetTags()
        {
            return new List<string>(); // _story?.currentTags ?? new List<string>();
        }

        public string SaveState()
        {
            return "{}"; // _story?.state.ToJson() ?? "{}";
        }

        public void LoadState(string json)
        {
            // _story?.state.LoadJson(json);
        }

        public void ResetStory()
        {
            // _story?.ResetState();
        }
    }
}
