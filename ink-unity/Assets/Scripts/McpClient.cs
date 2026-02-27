// HTTP client calling Steeltoe bridge for MCP tool access.
// Used for features not available in C# runtime: compile, debug, LLM, etc.

using UnityEngine;
using UnityEngine.Networking;
using System.Collections;
using System.Text;

namespace InkUnity
{
    public class McpClient : MonoBehaviour
    {
        [SerializeField] private string steeltoeUrl = "http://localhost:5000";

        /// <summary>Compile ink source via Steeltoe bridge → MCP server.</summary>
        public IEnumerator Compile(string source, System.Action<string> onResult)
        {
            var json = $"{{\"source\":\"{EscapeJson(source)}\"}}";
            yield return PostJson($"{steeltoeUrl}/api/story/compile", json, onResult);
        }

        /// <summary>Start a story via Steeltoe bridge (C# runtime).</summary>
        public IEnumerator StartStory(string compiledJson, System.Action<string> onResult)
        {
            var json = $"{{\"json\":{compiledJson}}}";
            yield return PostJson($"{steeltoeUrl}/api/story/start", json, onResult);
        }

        private IEnumerator PostJson(string url, string body, System.Action<string> callback)
        {
            using var request = new UnityWebRequest(url, "POST");
            var bodyBytes = Encoding.UTF8.GetBytes(body);
            request.uploadHandler = new UploadHandlerRaw(bodyBytes);
            request.downloadHandler = new DownloadHandlerBuffer();
            request.SetRequestHeader("Content-Type", "application/json");

            yield return request.SendWebRequest();

            if (request.result != UnityWebRequest.Result.Success)
            {
                Debug.LogError($"[McpClient] {request.error}");
                callback?.Invoke($"{{\"error\":\"{request.error}\"}}");
            }
            else
            {
                callback?.Invoke(request.downloadHandler.text);
            }
        }

        private static string EscapeJson(string s)
        {
            return s.Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\n", "\\n").Replace("\r", "\\r");
        }
    }
}
