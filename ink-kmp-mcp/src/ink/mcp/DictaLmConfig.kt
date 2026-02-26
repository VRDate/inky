package ink.mcp

/**
 * DictaLM 3.0 GGUF model configurations.
 *
 * DictaLM is a Hebrew-optimized LLM from Dicta (dicta-il).
 * Multiple quantization variants are available for different VRAM/GPU settings.
 *
 * IMPORTANT: JLama supports Mistral/Llama architectures but NOT Nemotron_h.
 * - 24B Thinking (Mistral-based): JLama compatible
 * - 1.7B (Llama-based): JLama compatible
 * - 12B Nemotron (Hybrid-SSM): NOT JLama compatible (use Ollama/vLLM instead)
 *
 * @see <a href="https://huggingface.co/collections/dicta-il/dictalm-30-collection">DictaLM 3.0 Collection</a>
 */
object DictaLmConfig {

    /** All available DictaLM 3.0 GGUF models with their specs */
    val MODELS = listOf(
        // ── 1.7B Thinking (smallest, Llama-based, JLama compatible) ──
        GgufModel(
            id = "thinking-1.7b",
            huggingFaceRepo = "dicta-il/DictaLM-3.0-1.7B-Thinking-GGUF",
            fileName = "DictaLM-3.0-1.7B-Thinking-Q4_K_M.gguf",
            parameters = "1.7B",
            quantization = "Q4_K_M",
            architecture = "llama",
            sizeGb = 1.1,
            minVramGb = 2,
            jlamaCompatible = true,
            description = "1.7B Thinking 4-bit — fast, fits any GPU, good for testing"
        ),

        // ── 24B Thinking (Mistral-based, JLama compatible) ──
        GgufModel(
            id = "thinking-24b-iq4",
            huggingFaceRepo = "dicta-il/DictaLM-3.0-24B-Thinking-GGUF",
            fileName = "DictaLM-3.0-24B-Thinking-IQ4_XS.gguf",
            parameters = "24B",
            quantization = "IQ4_XS",
            architecture = "mistral",
            sizeGb = 12.8,
            minVramGb = 16,
            jlamaCompatible = true,
            description = "24B Thinking iQuant 4-bit XS — smallest 24B variant"
        ),
        GgufModel(
            id = "thinking-24b-q4",
            huggingFaceRepo = "dicta-il/DictaLM-3.0-24B-Thinking-GGUF",
            fileName = "DictaLM-3.0-24B-Thinking-Q4_K_M.gguf",
            parameters = "24B",
            quantization = "Q4_K_M",
            architecture = "mistral",
            sizeGb = 14.3,
            minVramGb = 16,
            jlamaCompatible = true,
            description = "24B Thinking 4-bit — good quality, fits 16GB VRAM"
        ),
        GgufModel(
            id = "thinking-24b-q5",
            huggingFaceRepo = "dicta-il/DictaLM-3.0-24B-Thinking-GGUF",
            fileName = "DictaLM-3.0-24B-Thinking-Q5_K_M.gguf",
            parameters = "24B",
            quantization = "Q5_K_M",
            architecture = "mistral",
            sizeGb = 16.8,
            minVramGb = 24,
            jlamaCompatible = true,
            description = "24B Thinking 5-bit — high quality, needs 24GB VRAM"
        ),
        GgufModel(
            id = "thinking-24b-q6",
            huggingFaceRepo = "dicta-il/DictaLM-3.0-24B-Thinking-GGUF",
            fileName = "DictaLM-3.0-24B-Thinking-Q6_K.gguf",
            parameters = "24B",
            quantization = "Q6_K",
            architecture = "mistral",
            sizeGb = 19.3,
            minVramGb = 24,
            jlamaCompatible = true,
            description = "24B Thinking 6-bit — very high quality, needs 24GB VRAM"
        ),
        GgufModel(
            id = "thinking-24b-q8",
            huggingFaceRepo = "dicta-il/DictaLM-3.0-24B-Thinking-GGUF",
            fileName = "DictaLM-3.0-24B-Thinking-Q8_0.gguf",
            parameters = "24B",
            quantization = "Q8_0",
            architecture = "mistral",
            sizeGb = 25.1,
            minVramGb = 32,
            jlamaCompatible = true,
            description = "24B Thinking 8-bit — near lossless, needs 32GB VRAM"
        ),
        GgufModel(
            id = "thinking-24b-bf16",
            huggingFaceRepo = "dicta-il/DictaLM-3.0-24B-Thinking-GGUF",
            fileName = "DictaLM-3.0-24B-Thinking-BF16.gguf",
            parameters = "24B",
            quantization = "BF16",
            architecture = "mistral",
            sizeGb = 47.2,
            minVramGb = 48,
            jlamaCompatible = true,
            description = "24B Thinking full precision — needs 48GB+ VRAM"
        ),

        // ── FP8 variant (from VRDate) ──
        GgufModel(
            id = "thinking-24b-fp8-q4",
            huggingFaceRepo = "VRDate/DictaLM-3.0-24B-Thinking-FP8-Q4_K_S-GGUF",
            fileName = "dictalm-3.0-24b-thinking-fp8-q4_k_s.gguf",
            parameters = "24B",
            quantization = "FP8-Q4_K_S",
            architecture = "mistral",
            sizeGb = 13.5,
            minVramGb = 16,
            jlamaCompatible = true,
            description = "24B Thinking FP8→Q4 — compact high-quality quantization"
        ),

        // ── Nemotron 12B (NOT JLama compatible — Hybrid-SSM architecture) ──
        GgufModel(
            id = "nemotron-12b-q4",
            huggingFaceRepo = "dicta-il/DictaLM-3.0-Nemotron-12B-Instruct-GGUF",
            fileName = "DictaLM-3.0-Nemotron-12B-Instruct-Q4_K_M.gguf",
            parameters = "12B",
            quantization = "Q4_K_M",
            architecture = "nemotron_h",
            sizeGb = 7.49,
            minVramGb = 8,
            jlamaCompatible = false,
            description = "Nemotron 12B 4-bit — requires Ollama/vLLM (NOT JLama)"
        ),
        GgufModel(
            id = "nemotron-12b-q5",
            huggingFaceRepo = "dicta-il/DictaLM-3.0-Nemotron-12B-Instruct-GGUF",
            fileName = "DictaLM-3.0-Nemotron-12B-Instruct-Q5_K_M.gguf",
            parameters = "12B",
            quantization = "Q5_K_M",
            architecture = "nemotron_h",
            sizeGb = 8.76,
            minVramGb = 10,
            jlamaCompatible = false,
            description = "Nemotron 12B 5-bit — requires Ollama/vLLM (NOT JLama)"
        ),
        GgufModel(
            id = "nemotron-12b-q6",
            huggingFaceRepo = "dicta-il/DictaLM-3.0-Nemotron-12B-Instruct-GGUF",
            fileName = "DictaLM-3.0-Nemotron-12B-Instruct-Q6_K.gguf",
            parameters = "12B",
            quantization = "Q6_K",
            architecture = "nemotron_h",
            sizeGb = 10.1,
            minVramGb = 12,
            jlamaCompatible = false,
            description = "Nemotron 12B 6-bit — requires Ollama/vLLM (NOT JLama)"
        ),
        GgufModel(
            id = "nemotron-12b-q8",
            huggingFaceRepo = "dicta-il/DictaLM-3.0-Nemotron-12B-Instruct-GGUF",
            fileName = "DictaLM-3.0-Nemotron-12B-Instruct-Q8_0.gguf",
            parameters = "12B",
            quantization = "Q8_0",
            architecture = "nemotron_h",
            sizeGb = 13.1,
            minVramGb = 16,
            jlamaCompatible = false,
            description = "Nemotron 12B 8-bit — requires Ollama/vLLM (NOT JLama)"
        ),
        GgufModel(
            id = "nemotron-12b-bf16",
            huggingFaceRepo = "dicta-il/DictaLM-3.0-Nemotron-12B-Instruct-GGUF",
            fileName = "DictaLM-3.0-Nemotron-12B-Instruct-BF16.gguf",
            parameters = "12B",
            quantization = "BF16",
            architecture = "nemotron_h",
            sizeGb = 24.6,
            minVramGb = 32,
            jlamaCompatible = false,
            description = "Nemotron 12B full precision — requires Ollama/vLLM (NOT JLama)"
        )
    )

    /** Find best JLama-compatible model for given VRAM */
    fun recommendModel(availableVramGb: Int): GgufModel {
        return MODELS
            .filter { it.jlamaCompatible && it.minVramGb <= availableVramGb }
            .maxByOrNull { it.sizeGb } // largest that fits
            ?: MODELS.filter { it.jlamaCompatible }.minBy { it.sizeGb } // fallback to smallest
    }

    /** Find model by ID */
    fun findModel(id: String): GgufModel? = MODELS.find { it.id == id }

    /** List JLama-compatible models that fit given VRAM */
    fun modelsForVram(availableVramGb: Int): List<GgufModel> =
        MODELS.filter { it.jlamaCompatible && it.minVramGb <= availableVramGb }

    /** All JLama-compatible models */
    fun jlamaModels(): List<GgufModel> = MODELS.filter { it.jlamaCompatible }
}

data class GgufModel(
    val id: String,
    val huggingFaceRepo: String,
    val fileName: String,
    val parameters: String,
    val quantization: String,
    val architecture: String,
    val sizeGb: Double,
    val minVramGb: Int,
    val jlamaCompatible: Boolean,
    val description: String
) {
    /** Full HuggingFace model name for LangChain4j/JLama */
    val modelName: String get() = huggingFaceRepo

    /** HuggingFace URL */
    val url: String get() = "https://huggingface.co/$huggingFaceRepo/blob/main/$fileName"
}
