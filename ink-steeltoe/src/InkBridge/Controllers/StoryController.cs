// REST endpoints mirroring MCP tools — consumed by Unity WebGL client.

using InkBridge.Models;
using InkBridge.Services;
using Microsoft.AspNetCore.Mvc;

namespace InkBridge.Controllers;

[ApiController]
[Route("api/story")]
public class StoryController : ControllerBase
{
    private readonly InkRuntimeFactory _factory;

    public StoryController(InkRuntimeFactory factory) => _factory = factory;

    /// <summary>Compile ink source (proxies to MCP server).</summary>
    [HttpPost("compile")]
    public async Task<IActionResult> Compile([FromBody] CompileRequest req)
    {
        var result = await _factory.McpProxy.Compile(req.Source);
        return Ok(result);
    }

    /// <summary>Start a story from compiled JSON.</summary>
    [HttpPost("start")]
    public IActionResult Start([FromBody] StartRequest req)
    {
        var sessionId = _factory.CSharpRuntime.StartStory(req.Json);
        var state = _factory.CSharpRuntime.ContinueStory(sessionId);
        return Ok(new { sessionId, state });
    }

    /// <summary>Continue story.</summary>
    [HttpPost("{sessionId}/continue")]
    public IActionResult Continue(string sessionId)
    {
        var state = _factory.CSharpRuntime.ContinueStory(sessionId);
        return Ok(state);
    }

    /// <summary>Make a choice.</summary>
    [HttpPost("{sessionId}/choose/{index:int}")]
    public IActionResult Choose(string sessionId, int index)
    {
        var state = _factory.CSharpRuntime.Choose(sessionId, index);
        return Ok(state);
    }

    /// <summary>Get variable value.</summary>
    [HttpGet("{sessionId}/variable/{name}")]
    public IActionResult GetVariable(string sessionId, string name)
    {
        var value = _factory.CSharpRuntime.GetVariable(sessionId, name);
        return Ok(new { name, value });
    }

    /// <summary>Save story state.</summary>
    [HttpGet("{sessionId}/save")]
    public IActionResult SaveState(string sessionId)
    {
        var state = _factory.CSharpRuntime.SaveState(sessionId);
        return Ok(new { state });
    }

    /// <summary>Load story state.</summary>
    [HttpPost("{sessionId}/load")]
    public IActionResult LoadState(string sessionId, [FromBody] LoadStateRequest req)
    {
        _factory.CSharpRuntime.LoadState(sessionId, req.State);
        return Ok();
    }

    /// <summary>Reset story.</summary>
    [HttpPost("{sessionId}/reset")]
    public IActionResult Reset(string sessionId)
    {
        _factory.CSharpRuntime.ResetStory(sessionId);
        return Ok();
    }

    /// <summary>End session.</summary>
    [HttpDelete("{sessionId}")]
    public IActionResult End(string sessionId)
    {
        _factory.CSharpRuntime.EndSession(sessionId);
        return Ok();
    }
}

public record CompileRequest(string Source);
public record StartRequest(string Json);
public record LoadStateRequest(string State);
