// InkBridge — Steeltoe .NET 8 bridge between Kotlin MCP server and Unity C# ink runtime.
//
// Unified API that Unity WebGL calls. Dispatches to either:
// - Local C# ink runtime (Ink.Runtime.Story) for playback
// - Remote Kotlin MCP server (via proxy) for compile, debug, edit, LLM tools
//
// Steeltoe provides: service discovery, health checks, circuit breakers, tracing.

using InkBridge.Services;
using Steeltoe.Management.Endpoint;
using Steeltoe.Discovery.Client;

var builder = WebApplication.CreateBuilder(args);

// Steeltoe actuators (health, info, metrics)
builder.AddAllActuators();

// Steeltoe service discovery (Eureka/Consul) — discovers Kotlin MCP server
builder.AddDiscoveryClient();

// Register services
builder.Services.AddSingleton<InkCSharpRuntime>();
builder.Services.AddHttpClient<McpProxyService>();
builder.Services.AddSingleton<InkRuntimeFactory>();

builder.Services.AddControllers();
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
    {
        policy.AllowAnyOrigin().AllowAnyMethod().AllowAnyHeader();
    });
});

var app = builder.Build();

app.UseCors();
app.MapControllers();

// Steeltoe actuator endpoints
app.MapAllActuators();

app.MapGet("/", () => Results.Ok(new
{
    service = "InkBridge",
    version = "0.1.0",
    runtime = "C# Ink.Runtime + Steeltoe 4.0",
    endpoints = new[] { "/api/story/compile", "/api/story/start", "/api/story/{id}/continue", "/api/story/{id}/choose/{index}" }
}));

Console.WriteLine("╔══════════════════════════════════════════╗");
Console.WriteLine("║  InkBridge — Steeltoe .NET 8             ║");
Console.WriteLine("║  C# ink runtime + MCP proxy              ║");
Console.WriteLine($"║  Listening on: {app.Urls.FirstOrDefault() ?? "http://localhost:5000"}");
Console.WriteLine("╚══════════════════════════════════════════╝");

app.Run();
