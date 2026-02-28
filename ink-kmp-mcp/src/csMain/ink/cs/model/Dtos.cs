// ═══════════════════════════════════════════════════════════════
// Dtos.cs — Shared DTOs for ink runtime communication
// Used by both MAUI and Unity (no platform dependencies).
// Maps 1:1 with Kotlin ink.model proto messages.
// ═══════════════════════════════════════════════════════════════

using System;

namespace Ink.Common
{
    /// <summary>Result of ink source compilation.</summary>
    public class CompileResult
    {
        public string Json { get; set; } = "";
        public string[] Errors { get; set; } = Array.Empty<string>();
        public bool Success => Errors.Length == 0 && !string.IsNullOrEmpty(Json);
    }

    /// <summary>Story state snapshot after Continue/Choose.</summary>
    public class StoryStateDto
    {
        public string Text { get; set; } = "";
        public ChoiceDto[] Choices { get; set; } = Array.Empty<ChoiceDto>();
        public bool CanContinue { get; set; }
        public string[] Tags { get; set; } = Array.Empty<string>();
    }

    /// <summary>A single choice available to the player.</summary>
    public class ChoiceDto
    {
        public int Index { get; set; }
        public string Text { get; set; } = "";
    }

    /// <summary>Asset reference resolved from ink tags.</summary>
    public class AssetEventRefDto
    {
        public string Emoji { get; set; } = "";
        public string Category { get; set; } = "";
        public string Type { get; set; } = "";
        public string MeshPath { get; set; } = "";
        public string AnimSetId { get; set; } = "";
        public VoiceRefDto? VoiceRef { get; set; }
    }

    /// <summary>Voice synthesis reference.</summary>
    public class VoiceRefDto
    {
        public string CharacterId { get; set; } = "";
        public string Language { get; set; } = "";
        public string FlacPath { get; set; } = "";
    }

    /// <summary>Ink tag event from the story.</summary>
    public class InkTagEvent
    {
        public string SessionId { get; set; } = "";
        public string Knot { get; set; } = "";
        public string[] Tags { get; set; } = Array.Empty<string>();
        public AssetEventRefDto[] ResolvedAssets { get; set; } = Array.Empty<AssetEventRefDto>();
        public long Timestamp { get; set; }
    }

    /// <summary>Request to load an asset.</summary>
    public class AssetLoadRequest
    {
        public string SessionId { get; set; } = "";
        public AssetEventRefDto? Asset { get; set; }
        public string Priority { get; set; } = "normal";
        public long Timestamp { get; set; }
    }

    /// <summary>Inventory change event (equip/unequip).</summary>
    public class InventoryChangeEvent
    {
        public string SessionId { get; set; } = "";
        public string Action { get; set; } = "";
        public string Emoji { get; set; } = "";
        public string ItemName { get; set; } = "";
        public AssetEventRefDto? Asset { get; set; }
        public long Timestamp { get; set; }
    }

    /// <summary>Voice synthesis request.</summary>
    public class VoiceSynthRequest
    {
        public string SessionId { get; set; } = "";
        public string Text { get; set; } = "";
        public VoiceRefDto? VoiceRef { get; set; }
        public long Timestamp { get; set; }
    }

    /// <summary>Emoji → prefab path mapping (configurable per platform).</summary>
    public class EmojiPrefabMapping
    {
        public string Emoji { get; set; } = "";
        public string PrefabPath { get; set; } = "";
        public string? AnimSetOverride { get; set; }
    }
}
