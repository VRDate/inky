namespace InkBridge.Models;

public record StorySession(string SessionId, string Json, DateTime CreatedAt);

public record CompileResult(string Json, string[] Errors);

public record StoryState(string Text, Choice[] Choices, bool CanContinue, string[] Tags);

public record Choice(int Index, string Text);
