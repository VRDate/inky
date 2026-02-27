// Proxies requests to the Kotlin MCP server for features not available in C#:
// compile (inkjs/inklecate), debug, edit, PlantUML, LLM, calendar, vCard, etc.
//
// Uses Steeltoe service discovery to find the MCP server.

using System.Text.Json;

namespace InkBridge.Services;

/// <summary>
/// HTTP client forwarding tool calls to the Kotlin MCP server.
/// Steeltoe service discovery resolves the MCP server address.
/// </summary>
public class McpProxyService
{
    private readonly HttpClient _http;
    private readonly ILogger<McpProxyService> _logger;

    public McpProxyService(HttpClient http, ILogger<McpProxyService> logger)
    {
        _http = http;
        _logger = logger;
        // Default MCP server URL — overridden by Steeltoe service discovery
        _http.BaseAddress = new Uri("http://localhost:3001");
    }

    /// <summary>Call an MCP tool on the Kotlin server.</summary>
    public async Task<JsonElement> CallTool(string toolName, Dictionary<string, object> args)
    {
        var request = new
        {
            jsonrpc = "2.0",
            id = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
            method = "tools/call",
            @params = new { name = toolName, arguments = args }
        };

        var json = JsonSerializer.Serialize(request);
        var content = new StringContent(json, System.Text.Encoding.UTF8, "application/json");

        _logger.LogDebug("MCP proxy: {Tool} -> {Url}", toolName, _http.BaseAddress);

        var response = await _http.PostAsync("/message", content);
        response.EnsureSuccessStatusCode();

        var responseJson = await response.Content.ReadAsStringAsync();
        var doc = JsonDocument.Parse(responseJson);
        return doc.RootElement;
    }

    /// <summary>Compile ink source via MCP server (uses inkjs/inklecate).</summary>
    public async Task<JsonElement> Compile(string source)
    {
        return await CallTool("compile_ink", new Dictionary<string, object> { ["source"] = source });
    }
}
