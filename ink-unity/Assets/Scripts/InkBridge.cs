// InkBridge — Mediates between Unity UI and ink runtimes.
//
// Dual-runtime approach:
// 1. Primary: C# Ink.Runtime.Story (native, from Assets/Ink/InkRuntime/)
// 2. Fallback: inkjs via OneJS (V8 engine) when C# has compatibility issues
//
// All C# code compiles for WebGL (IL2CPP).

using UnityEngine;
using System.Collections.Generic;

namespace InkUnity
{
    public class InkBridge : MonoBehaviour
    {
        [Header("Runtime Selection")]
        [SerializeField] private bool useFallback = false;

        private InkStoryRunner _csharpRunner;
        private InkJsFallback _jsFallback;

        private void Awake()
        {
            _csharpRunner = GetComponent<InkStoryRunner>();
            _jsFallback = GetComponent<InkJsFallback>();
        }

        /// <summary>Start a story from compiled JSON.</summary>
        public string StartStory(string json)
        {
            if (useFallback && _jsFallback != null)
                return _jsFallback.StartStory(json);
            return _csharpRunner.StartStory(json);
        }

        /// <summary>Continue the story, returning text.</summary>
        public string ContinueStory()
        {
            if (useFallback && _jsFallback != null)
                return _jsFallback.ContinueStory();
            return _csharpRunner.ContinueStory();
        }

        /// <summary>Get current choices.</summary>
        public List<string> GetChoices()
        {
            if (useFallback && _jsFallback != null)
                return _jsFallback.GetChoices();
            return _csharpRunner.GetChoices();
        }

        /// <summary>Make a choice by index.</summary>
        public void Choose(int index)
        {
            if (useFallback && _jsFallback != null)
                _jsFallback.Choose(index);
            else
                _csharpRunner.Choose(index);
        }

        /// <summary>Check if story can continue.</summary>
        public bool CanContinue
        {
            get
            {
                if (useFallback && _jsFallback != null)
                    return _jsFallback.CanContinue;
                return _csharpRunner.CanContinue;
            }
        }

        /// <summary>Get current tags.</summary>
        public List<string> GetTags()
        {
            if (useFallback && _jsFallback != null)
                return _jsFallback.GetTags();
            return _csharpRunner.GetTags();
        }

        /// <summary>Save story state to JSON.</summary>
        public string SaveState()
        {
            if (useFallback && _jsFallback != null)
                return _jsFallback.SaveState();
            return _csharpRunner.SaveState();
        }

        /// <summary>Load story state from JSON.</summary>
        public void LoadState(string stateJson)
        {
            if (useFallback && _jsFallback != null)
                _jsFallback.LoadState(stateJson);
            else
                _csharpRunner.LoadState(stateJson);
        }

        /// <summary>Reset story to beginning.</summary>
        public void ResetStory()
        {
            if (useFallback && _jsFallback != null)
                _jsFallback.ResetStory();
            else
                _csharpRunner.ResetStory();
        }
    }
}
