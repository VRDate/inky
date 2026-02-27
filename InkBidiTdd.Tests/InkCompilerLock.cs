using Ink;
using Ink.Runtime;

namespace InkBidiTdd.Tests;

/// <summary>
/// Thread-safe wrapper around the ink C# compiler.
///
/// The ink compiler uses non-thread-safe static state (CharacterSet with HashSet,
/// InkParser shared parsing tables). This class serializes compilation via a
/// SemaphoreSlim while leaving per-session Story operations fully parallel —
/// the same pattern Java/JS runtimes get for free from their VMs.
/// </summary>
public static class InkCompilerLock
{
    private static readonly SemaphoreSlim CompileSemaphore = new(1, 1);

    /// <summary>
    /// Compile ink source under the shared semaphore.
    /// Returns (story, errors). Story is null on failure.
    /// </summary>
    public static (Story? story, List<string> errors) Compile(string source)
    {
        var errors = new List<string>();
        CompileSemaphore.Wait();
        try
        {
            var compiler = new Compiler(source, new Compiler.Options
            {
                errorHandler = (message, type) =>
                {
                    if (type == ErrorType.Error)
                        errors.Add(message);
                }
            });
            var story = compiler.Compile();
            return (story, errors);
        }
        finally
        {
            CompileSemaphore.Release();
        }
    }

    /// <summary>
    /// Async compile for use in async test methods.
    /// </summary>
    public static async Task<(Story? story, List<string> errors)> CompileAsync(string source)
    {
        var errors = new List<string>();
        await CompileSemaphore.WaitAsync();
        try
        {
            var compiler = new Compiler(source, new Compiler.Options
            {
                errorHandler = (message, type) =>
                {
                    if (type == ErrorType.Error)
                        errors.Add(message);
                }
            });
            var story = compiler.Compile();
            return (story, errors);
        }
        finally
        {
            CompileSemaphore.Release();
        }
    }
}
