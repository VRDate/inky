// OneJS fallback — runs inkjs JavaScript when C# runtime has compatibility issues.
// OneJS provides a V8 JS engine inside Unity.

using UnityEngine;
using System.Collections.Generic;

namespace InkUnity
{
    public class InkJsFallback : MonoBehaviour
    {
        // OneJS engine reference (set in inspector or via GetComponent)
        // private OneJS.Engine _jsEngine;

        public string StartStory(string json)
        {
            // _jsEngine.Evaluate($"var story = new inkjs.Story({json});");
            Debug.Log("[InkJsFallback] Story started (inkjs via OneJS)");
            return "unity-onejs-1";
        }

        public string ContinueStory()
        {
            // return _jsEngine.Evaluate<string>("(function() { var t=''; while(story.canContinue) t+=story.Continue(); return t; })()");
            return "[inkjs fallback — connect OneJS to enable]";
        }

        public List<string> GetChoices()
        {
            // var json = _jsEngine.Evaluate<string>("JSON.stringify(story.currentChoices.map(c=>c.text))");
            return new List<string>();
        }

        public void Choose(int index)
        {
            // _jsEngine.Evaluate($"story.ChooseChoiceIndex({index})");
        }

        public bool CanContinue => false;
        // get => _jsEngine.Evaluate<bool>("story.canContinue");

        public List<string> GetTags()
        {
            return new List<string>();
        }

        public string SaveState()
        {
            // return _jsEngine.Evaluate<string>("story.state.toJson()");
            return "{}";
        }

        public void LoadState(string json)
        {
            // _jsEngine.Evaluate($"story.state.LoadJson('{json}')");
        }

        public void ResetStory()
        {
            // _jsEngine.Evaluate("story.ResetState()");
        }
    }
}
