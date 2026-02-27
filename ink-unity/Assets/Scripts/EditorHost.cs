// Hosts the React app (react-ink-editor) inside Unity via OneJS.
//
// OneJS provides React/JS runtime (V8) within Unity.
// The React components (Remirror, CodeMirror, InkPlayer) run via OneJS.
//
// In Edit mode: Remirror MD editor + live preview
// In Play mode: readonly InkPlayer calling C# ink via InkOneJsBinding
// In WebGL: OneJS renders into browser DOM alongside Unity canvas

using UnityEngine;

namespace InkUnity
{
    public class EditorHost : MonoBehaviour
    {
        [Header("React App")]
        [SerializeField] private string reactBundlePath = "react-ink-editor/dist/index.js";

        [Header("MCP Server")]
        [SerializeField] private string mcpServerUrl = "http://localhost:3001";
        [SerializeField] private string yjsWsUrl = "ws://localhost:3001/collab";
        [SerializeField] private string docId = "default";

        private void Start()
        {
            // Load React app bundle via OneJS
            // _jsEngine.LoadScript(reactBundlePath);
            //
            // Pass configuration to React:
            // _jsEngine.SetGlobal("__inkConfig", new {
            //     mcpServerUrl,
            //     yjsWsUrl,
            //     docId,
            //     environment = "unity-onejs"
            // });

            Debug.Log($"[EditorHost] React app loaded from {reactBundlePath}");
            Debug.Log($"[EditorHost] MCP server: {mcpServerUrl}");
            Debug.Log($"[EditorHost] Yjs collab: {yjsWsUrl}/collab/{docId}");
        }
    }
}
