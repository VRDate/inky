// Routes requests to the appropriate runtime:
// - C# runtime for story playback (fast, local)
// - MCP proxy for compile, debug, edit, PlantUML, LLM tools (server-side)

namespace InkBridge.Services;

/// <summary>
/// Factory that routes ink operations to the right backend.
/// C# for playback, MCP proxy for everything else.
/// </summary>
public class InkRuntimeFactory
{
    private readonly InkCSharpRuntime _csharpRuntime;
    private readonly McpProxyService _mcpProxy;

    public InkRuntimeFactory(InkCSharpRuntime csharpRuntime, McpProxyService mcpProxy)
    {
        _csharpRuntime = csharpRuntime;
        _mcpProxy = mcpProxy;
    }

    public InkCSharpRuntime CSharpRuntime => _csharpRuntime;
    public McpProxyService McpProxy => _mcpProxy;
}
