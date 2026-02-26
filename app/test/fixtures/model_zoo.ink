// model_zoo.ink — Interactive LangChain4j/JLama Model Zoo Manager
// Manages DictaLM 3.0 GGUF model selection based on user's hardware.
// Designed for the Inky MCP Server's generate_compile_play pipeline.

# author: Inky MCP
# theme: tech

VAR vram_gb = 0
VAR selected_model = ""
VAR selected_quant = ""
VAR selected_size = 0.0
VAR model_loaded = false
VAR architecture = ""

-> welcome

=== welcome ===
🧠 LangChain4j Model Zoo — DictaLM 3.0 GGUF Manager

Welcome to the interactive model selection wizard.
This tool helps you choose the right DictaLM 3.0 model
for your hardware configuration.

DictaLM 3.0 is a Hebrew-optimized LLM from DICTA,
available in three size tiers:
  • 1.7B parameters (Llama-based)
  • 12B parameters (Nemotron Hybrid-SSM)
  • 24B parameters (Mistral-based)

* [Let's get started!] -> detect_hardware
* [Show me all models first] -> model_catalog
* [I know what I want] -> direct_select

=== detect_hardware ===
First, let's figure out your GPU setup.

How much VRAM does your GPU have?
* [2 GB or less (integrated GPU)]
    ~ vram_gb = 2
    -> recommend_model
* [4 GB (entry-level GPU)]
    ~ vram_gb = 4
    -> recommend_model
* [8 GB (RTX 3060/4060)]
    ~ vram_gb = 8
    -> recommend_model
* [16 GB (RTX 4080/A4000)]
    ~ vram_gb = 16
    -> recommend_model
* [24 GB (RTX 3090/4090)]
    ~ vram_gb = 24
    -> recommend_model
* [32 GB+ (A100/H100)]
    ~ vram_gb = 32
    -> recommend_model
* [48 GB+ (multi-GPU or A100-80)]
    ~ vram_gb = 48
    -> recommend_model

=== recommend_model ===
{vram_gb} GB VRAM detected. Let me find the best model for you...

{
    - vram_gb <= 2:
        ~ selected_model = "thinking-1.7b"
        ~ selected_quant = "Q4_K_M"
        ~ selected_size = 1.1
        ~ architecture = "llama"
        ✅ Recommended: **DictaLM 3.0 1.7B Thinking** (Q4_K_M, 1.1 GB)
        This compact model fits easily in your VRAM.
        Perfect for testing and development.
    - vram_gb <= 8:
        ~ selected_model = "thinking-1.7b"
        ~ selected_quant = "Q4_K_M"
        ~ selected_size = 1.1
        ~ architecture = "llama"
        ✅ Recommended: **DictaLM 3.0 1.7B Thinking** (Q4_K_M, 1.1 GB)
        Plenty of room! You could also try the Nemotron 12B via Ollama.
        Note: The 12B Nemotron models require Ollama/vLLM (not JLama).
    - vram_gb <= 16:
        ~ selected_model = "thinking-24b-q4"
        ~ selected_quant = "Q4_K_M"
        ~ selected_size = 14.3
        ~ architecture = "mistral"
        ✅ Recommended: **DictaLM 3.0 24B Thinking** (Q4_K_M, 14.3 GB)
        Great balance of quality and speed for your GPU.
    - vram_gb <= 24:
        ~ selected_model = "thinking-24b-q5"
        ~ selected_quant = "Q5_K_M"
        ~ selected_size = 16.8
        ~ architecture = "mistral"
        ✅ Recommended: **DictaLM 3.0 24B Thinking** (Q5_K_M, 16.8 GB)
        High quality with your generous VRAM budget.
    - vram_gb <= 32:
        ~ selected_model = "thinking-24b-q8"
        ~ selected_quant = "Q8_0"
        ~ selected_size = 25.1
        ~ architecture = "mistral"
        ✅ Recommended: **DictaLM 3.0 24B Thinking** (Q8_0, 25.1 GB)
        Near-lossless quality!
    - else:
        ~ selected_model = "thinking-24b-bf16"
        ~ selected_quant = "BF16"
        ~ selected_size = 47.2
        ~ architecture = "mistral"
        ✅ Recommended: **DictaLM 3.0 24B Thinking** (BF16, 47.2 GB)
        Full precision — maximum quality!
}

Architecture: {architecture} (JLama compatible ✅)
Download size: ~{selected_size} GB

* [Load this model] -> load_model
* [Show me other options] -> alternative_models
* [Back to catalog] -> model_catalog

=== alternative_models ===
Here are all JLama-compatible models that fit your {vram_gb} GB VRAM:

{
    - vram_gb >= 2:
        • 1.7B Thinking Q4 — 1.1 GB (llama arch)
    - else:
        (No models fit your VRAM. Consider Ollama for streaming.)
}
{
    - vram_gb >= 16:
        • 24B Thinking IQ4_XS — 12.8 GB (mistral arch)
        • 24B Thinking Q4_K_M — 14.3 GB (mistral arch)
        • 24B FP8→Q4 — 13.5 GB (mistral arch, VRDate quant)
}
{
    - vram_gb >= 24:
        • 24B Thinking Q5_K_M — 16.8 GB (mistral arch)
        • 24B Thinking Q6_K — 19.3 GB (mistral arch)
}
{
    - vram_gb >= 32:
        • 24B Thinking Q8_0 — 25.1 GB (mistral arch)
}
{
    - vram_gb >= 48:
        • 24B Thinking BF16 — 47.2 GB (mistral arch, full precision)
}

* [Use the recommended model ({selected_model})] -> load_model
* [Select 1.7B model]
    ~ selected_model = "thinking-1.7b"
    ~ selected_quant = "Q4_K_M"
    ~ selected_size = 1.1
    ~ architecture = "llama"
    -> load_model
* {vram_gb >= 16} [Select 24B Q4]
    ~ selected_model = "thinking-24b-q4"
    ~ selected_quant = "Q4_K_M"
    ~ selected_size = 14.3
    ~ architecture = "mistral"
    -> load_model
* {vram_gb >= 24} [Select 24B Q5]
    ~ selected_model = "thinking-24b-q5"
    ~ selected_quant = "Q5_K_M"
    ~ selected_size = 16.8
    ~ architecture = "mistral"
    -> load_model
* {vram_gb >= 32} [Select 24B Q8]
    ~ selected_model = "thinking-24b-q8"
    ~ selected_quant = "Q8_0"
    ~ selected_size = 25.1
    ~ architecture = "mistral"
    -> load_model

=== load_model ===
Loading **{selected_model}** ({selected_quant}, ~{selected_size} GB)...

This will download the model from HuggingFace on first run.
The model will be cached in ~/.jlama for future use.

# load_model: {selected_model}
~ model_loaded = true

✅ Model loaded successfully!

What would you like to do with it?

* [Chat in Hebrew] -> chat_demo
* [Generate ink code] -> generate_demo
* [Translate ink to Hebrew] -> translate_demo
* [Review ink code] -> review_demo
* [Full pipeline: generate → compile → play] -> pipeline_demo
* [Back to menu] -> welcome

=== chat_demo ===
# tool: llm_chat

💬 **Hebrew Chat Demo**

The loaded model ({selected_model}) is optimized for Hebrew.
Type a message in Hebrew or English and it will respond.

Example prompts:
  • "ספר לי סיפור קצר על ירושלים"
  • "מה ההבדל בין ink לבין Twine?"
  • "Translate 'Once upon a time' to Hebrew"

* [Try another feature] -> load_model
* [Back to menu] -> welcome

=== generate_demo ===
# tool: generate_ink

✍️ **Ink Generation Demo**

The model will generate ink interactive fiction code
from a natural language description.

Example prompts:
  • "A mystery story in a Jerusalem market"
  • "A branching dialogue with an old rabbi"
  • "A puzzle game about Hebrew letters"

The generated code is automatically compiled and validated.

* [Try another feature] -> load_model
* [Back to menu] -> welcome

=== translate_demo ===
# tool: translate_ink_hebrew

🌐 **Hebrew Translation Demo** (via Camel route)

Paste your ink source and the model will translate
story text to Hebrew while preserving ink syntax.

The translated text can then be bidified for proper
RTL display using the bidify tool.

Pipeline: source → translate → bidify → compile → play

* [Try another feature] -> load_model
* [Back to menu] -> welcome

=== review_demo ===
# tool: review_ink

🔍 **Ink Review Demo**

The model will analyze your ink code for:
  • Syntax errors
  • Dead-end paths (missing diverts)
  • Unused knots/stitches
  • Logic inconsistencies
  • RTL/bidi considerations

* [Try another feature] -> load_model
* [Back to menu] -> welcome

=== pipeline_demo ===
# tool: generate_compile_play

🚀 **Full Pipeline Demo** (via Camel route chain)

This chains three operations:
  1. LLM generates ink code from your prompt
  2. InkEngine compiles it via GraalJS + inkjs
  3. Story session starts for interactive play

All powered by Apache Camel route orchestration!

* [Try another feature] -> load_model
* [Back to menu] -> welcome

=== model_catalog ===
📋 **Complete DictaLM 3.0 GGUF Catalog**

━━ JLama Compatible (Mistral/Llama arch) ━━

│ ID                    │ Params │ Quant    │ Size   │ VRAM  │
│ thinking-1.7b         │ 1.7B   │ Q4_K_M   │ 1.1 GB │ 2 GB  │
│ thinking-24b-iq4      │ 24B    │ IQ4_XS   │ 12.8 GB│ 16 GB │
│ thinking-24b-q4       │ 24B    │ Q4_K_M   │ 14.3 GB│ 16 GB │
│ thinking-24b-fp8-q4   │ 24B    │ FP8-Q4   │ 13.5 GB│ 16 GB │
│ thinking-24b-q5       │ 24B    │ Q5_K_M   │ 16.8 GB│ 24 GB │
│ thinking-24b-q6       │ 24B    │ Q6_K     │ 19.3 GB│ 24 GB │
│ thinking-24b-q8       │ 24B    │ Q8_0     │ 25.1 GB│ 32 GB │
│ thinking-24b-bf16     │ 24B    │ BF16     │ 47.2 GB│ 48 GB │

━━ Ollama/vLLM Only (Nemotron Hybrid-SSM arch) ━━

│ nemotron-12b-q4       │ 12B    │ Q4_K_M   │ 7.5 GB │ 8 GB  │
│ nemotron-12b-q5       │ 12B    │ Q5_K_M   │ 8.8 GB │ 10 GB │
│ nemotron-12b-q6       │ 12B    │ Q6_K     │ 10.1 GB│ 12 GB │
│ nemotron-12b-q8       │ 12B    │ Q8_0     │ 13.1 GB│ 16 GB │
│ nemotron-12b-bf16     │ 12B    │ BF16     │ 24.6 GB│ 32 GB │

* [Select by VRAM] -> detect_hardware
* [Select directly] -> direct_select
* [Back to menu] -> welcome

=== direct_select ===
Choose a model directly:

* [1.7B Thinking Q4 (1.1 GB)]
    ~ selected_model = "thinking-1.7b"
    ~ selected_quant = "Q4_K_M"
    ~ selected_size = 1.1
    ~ architecture = "llama"
    -> load_model
* [24B Thinking Q4 (14.3 GB)]
    ~ selected_model = "thinking-24b-q4"
    ~ selected_quant = "Q4_K_M"
    ~ selected_size = 14.3
    ~ architecture = "mistral"
    -> load_model
* [24B Thinking Q5 (16.8 GB)]
    ~ selected_model = "thinking-24b-q5"
    ~ selected_quant = "Q5_K_M"
    ~ selected_size = 16.8
    ~ architecture = "mistral"
    -> load_model
* [24B Thinking Q8 (25.1 GB)]
    ~ selected_model = "thinking-24b-q8"
    ~ selected_quant = "Q8_0"
    ~ selected_size = 25.1
    ~ architecture = "mistral"
    -> load_model
* [Back to catalog] -> model_catalog

-> END
